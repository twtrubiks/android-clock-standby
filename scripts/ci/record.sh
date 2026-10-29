#!/usr/bin/env bash
# 在模擬器上一邊錄影一邊跑 DemoRecordingTest。
# 輸出到 $1（預設 out/）：demo.raw.mp4、instrument.txt、logcat.txt、screenshots/，
# 以及 release 版冒煙測試的 release-smoke.txt、logcat-release.txt、screenshots/release-*.png
# 由 .github/workflows/android-record.yml 在 android-emulator-runner 裡呼叫。
set -uo pipefail

OUT=${1:-out}
APP_ID=com.twtrubiks.clockstandby
TEST_CLASS=$APP_ID.DemoRecordingTest
mkdir -p "$OUT"

adb install -r -t app/build/outputs/apk/debug/app-debug.apk || exit 1
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit 1

# 喚醒並解鎖螢幕、在影片裡顯示點擊位置
# （不用 keyevent 82：MENU 鍵在桌面會叫出長按選單，被錄進影片開頭）
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell settings put system show_touches 1 || true
# 不要跳「Viewing full screen」提示（待機畫面會隱藏系統列）
adb shell settings put secure immersive_mode_confirmations confirmed || true

# 剛開機時系統服務還很忙：System UI 會 ANR、測試要幾十秒才啟動。
# 等 1 分鐘 load average 降到 2.5 以下再開始，最多等 3 分鐘。
for _ in $(seq 1 36); do
  load=$(adb shell cat /proc/loadavg | cut -d' ' -f1)
  echo "loadavg: $load"
  awk -v l="$load" 'BEGIN { exit !(l < 2.5) }' && break
  sleep 5
done

# 開機時卡死的系統程式（Quickstep、System UI 等）會一直跳 ANR 對話框，按 Wait 5 秒後又會再跳，
# 對話框在的時候 UiAutomator 也讀不到 App 畫面。錄影前直接重新啟動它們（系統會自動重開）。
anr_pkgs=$(adb shell dumpsys window windows | grep -o 'Application Not Responding: [A-Za-z0-9_.]*' | sed 's/.*: //' | sort -u)
for pkg in $anr_pkgs; do
  echo "重新啟動 ANR 的程式：$pkg"
  adb shell am force-stop "$pkg" || true
done
if [ -n "$anr_pkgs" ]; then
  sleep 5
fi

adb logcat -c || true

# pixel_7_pro 是 1440x3120，直接錄可能超過模擬器編碼器上限，所以錄一半尺寸
# screenrecord 單次最長 180 秒
adb shell screenrecord --size 720x1560 --bit-rate 8000000 --time-limit 180 /sdcard/demo.mp4 &
REC_PID=$!
sleep 2

adb shell am instrument -w -e class "$TEST_CLASS" \
  "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument.txt"

sleep 1
# Android 7 的 pkill 是舊版 toolbox，不認得 -INT，要寫成 -l 2（SIGINT）
adb shell 'pkill -INT screenrecord 2>/dev/null || pkill -l 2 screenrecord' || true
wait "$REC_PID" || true
# 等 screenrecord 把檔案寫完
sleep 2

adb pull /sdcard/demo.mp4 "$OUT/demo.raw.mp4" || true
# DemoRecordingTest 在關鍵畫面存的截圖（橫屏畫面在影片裡會被縮小）
adb pull "/sdcard/Android/data/$APP_ID/files/screenshots" "$OUT/" || true
adb logcat -d > "$OUT/logcat.txt" || true

status=0
# 測試失敗時 am instrument 的 exit code 仍是 0，要看輸出判斷
if ! grep -q '^OK (' "$OUT/instrument.txt"; then
  echo "DemoRecordingTest 沒有通過，詳見 $OUT/instrument.txt 與 $OUT/logcat.txt"
  status=1
fi

# release 版冒煙測試：R8 壓縮過的程式在 debug 版測不到，覆蓋安裝後三種錶面和螢幕保護程式各截一張圖，確認沒有閃退。
# 兩個版本都用 debug 金鑰簽，可以直接覆蓋；展示流程已經看過介紹頁，會直接進到待機畫面
RELEASE_APK=app/build/outputs/apk/release/app-release.apk
if [ -f "$RELEASE_APK" ]; then
  mkdir -p "$OUT/screenshots"
  adb install -r "$RELEASE_APK" || exit 1
  adb logcat -c || true
  adb shell am start -W -n "$APP_ID/.MainActivity" | tee "$OUT/release-smoke.txt"
  size=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
  w=${size%x*}
  h=${size#*x}
  for face in 1-digital 2-analog 3-flip; do
    sleep 3
    adb exec-out screencap -p > "$OUT/screenshots/release-$face.png"
    # 左滑換下一個錶面
    adb shell input swipe $((w * 4 / 5)) $((h / 2)) $((w / 5)) $((h / 2)) 300
  done
  # 充電待機（螢幕保護程式）：設成目前的螢幕保護程式，叫 SystemUI 直接啟動，截一張圖確認畫面與沒有閃退
  adb shell settings put secure screensaver_enabled 1 || true
  adb shell settings put secure screensaver_components "$APP_ID/.ClockDreamService" || true
  adb shell am start -n com.android.systemui/.Somnambulator || true
  sleep 4
  adb exec-out screencap -p > "$OUT/screenshots/release-4-dream.png"
  # 螢幕保護程式設成不能互動，碰一下就結束
  adb shell input tap $((w / 2)) $((h / 2)) || true
  sleep 2
  adb logcat -d > "$OUT/logcat-release.txt" || true
  if grep -q 'FATAL EXCEPTION' "$OUT/logcat-release.txt"; then
    echo "release 版閃退，詳見 $OUT/logcat-release.txt"
    status=1
  fi
fi

exit $status
