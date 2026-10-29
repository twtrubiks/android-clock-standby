# 時鐘待機（Clock Standby）

Kotlin + Jetpack Compose 原生 Android App，全程在 Claude Code 雲端 session 開發。
雲端 VM 沒有模擬器，模擬器操作與錄影交給 GitHub Actions（`.github/workflows/android-record.yml`）。

## 專案約定

- applicationId / namespace：`com.twtrubiks.clockstandby`（`scripts/ci/record.sh` 依賴這個值，改了要一起改）。
  repo 改名時不要跟著改：applicationId 一變，手機會當成另一個 App，設定和螢幕保護程式的選擇都不會帶過去
- 文件裡不要寫死 repo 名稱：`gh api` 用 `repos/{owner}/{repo}/...`（`gh` 會從 git remote 帶入），workflow 用 `${GITHUB_REPOSITORY}`
- 單一 module `app`，主畫面 `MainActivity`；充電待機是系統螢幕保護程式 `ClockDreamService`（不要改成常駐的前景服務）
- minSdk 24（Android 7.0），compileSdk 37，targetSdk 36，Java / Kotlin target 17
  （`java.time` 在 API 26 才內建，靠 core library desugaring 補上；新增 API 26+ 的 API 前先跑 `./gradlew lintDebug` 看 NewApi）
  （Compose 1.12 / core 1.19 的 AAR metadata 要求 compileSdk ≥ 37；缺 `platforms;android-37.0` 時 AGP 會自動下載）
- 依賴版本集中在 `gradle/libs.versions.toml`
- release 版開 R8（minify + shrinkResources），不上架所以用 debug 金鑰簽，可以和同一台機器打包的 debug 版互相覆蓋安裝。
  debug 金鑰在 VM 的 `~/.android/debug.keystore`，換 session 會變，之後給使用者的 APK 簽章就對不上，要先移除舊版
- 使用者要 APK 時給 release 版（`app/build/outputs/apk/release/app-release.apk`），
  複製到 scratchpad 改名成 `clock-standby-<commit>.apk`，再用 SendUserFile（`display: attach`）傳送
- AGP 9.x 使用 built-in Kotlin：**不要**套 `org.jetbrains.kotlin.android`，Compose 要套 `org.jetbrains.kotlin.plugin.compose`
- 版本起點（2026-10 查的最新穩定版）：AGP 9.4.1、Kotlin / Compose compiler plugin 2.4.21、Gradle wrapper 9.8.2、
  Compose BOM 2026.09.00、activity-compose 1.13.0、core-ktx 1.19.1、lifecycle-runtime-ktx 2.11.0、
  androidx.test runner 1.7.0、core 1.7.0、ext:junit 1.3.0、uiautomator 2.4.0、desugar_jdk_libs 2.1.5。
  若 build 報版本相容問題，以錯誤訊息為準調整，並更新這段

## 雲端 VM 環境

- Android SDK 由環境的 setup script（`scripts/cloud-setup.sh`）裝在 `/opt/android-sdk`，`ANDROID_HOME` 由環境變數提供
- SDK 不存在時先看 `/var/log/android-sdk-setup.log`
- 還沒有 `gradlew` 時，用 VM 預裝的 Gradle 產生：`gradle wrapper --gradle-version 9.8.2`
- 新 session 第一次 build 可能遇到 Maven Central 回 `429 Too Many Requests`（共用出口 IP 被限流），
  隔 20 秒左右重跑 `./gradlew` 即可，依賴進快取後就不會再遇到；不要為此改 repository 設定（CI 不受影響）

## 在 VM 內驗證（每次 push 前）

```bash
./gradlew assembleDebug assembleRelease testDebugUnitTest assembleDebugAndroidTest
```

`assembleRelease` 會跑 R8，可以先抓到缺 class 之類的錯誤；執行時才會出現的問題要看 CI 的 release 冒煙測試。

## 錄影測試：DemoRecordingTest

`app/src/androidTest/java/com/twtrubiks/clockstandby/DemoRecordingTest.kt`，CI 只跑這個 class，
`record.sh` 在它執行期間用 `screenrecord` 錄整個螢幕。

- 用 UiAutomator 操作（真實時間驅動，動畫會正常播放），不要用 Compose test rule
- 一個 `@Test` 跑完整的展示流程，每個操作之間停 1～1.5 秒讓影片看得清楚
- 全長控制在 150 秒內（`screenrecord` 上限 180 秒）
- 用 `By.text(...)` 找元件；要用 testTag 時，在 root 加 `Modifier.semantics { testTagsAsResourceId = true }` 再用 `By.res(...)`
- `screenrecord` 是固定直式尺寸，轉成橫屏後畫面會被縮成影片中間一條、影格看不清楚；
  要確認細節的畫面用測試裡的 `screenshot("名稱")` 另存截圖

```kotlin
@RunWith(AndroidJUnit4::class)
class DemoRecordingTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun demo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            pause()
            device.findObject(By.text("…")).click()
            pause()
        }
    }

    private fun pause(ms: Long = 1200) {
        device.waitForIdle()
        SystemClock.sleep(ms)
    }
}
```

## 錄影流程（GitHub Actions）

1. push 到目前的 branch；改到 `app/**`、`gradle/**`、`*.gradle.kts`、`gradle.properties` 會自動觸發。
   只改 `scripts/ci/**` 或 workflow 時要手動觸發：
   `gh api -X POST 'repos/{owner}/{repo}/actions/workflows/android-record.yml/dispatches' -f ref=<branch>`
   錄影一律用 API 24（最低支援的 Android 7.0）的模擬器，push 和手動觸發都是；不要另外再跑其他 API level。
   使用者明確要求其他版本時才在手動觸發加上 `-f "inputs[api-level]=<level>"`（例如 Android 14 是 34），
   不同 API level 會同時跑，但都寫到同一個 media branch，用 `info.txt` 的 `api level` 分辨。
   只改文件（README、CLAUDE.md、`docs/**`）不會觸發；萬一只改文件卻觸發了 build 或錄影，不用問使用者，直接取消：
   `gh api -X POST 'repos/{owner}/{repo}/actions/runs/<id>/cancel'`
2. 查進度：`gh api "repos/{owner}/{repo}/actions/runs?branch=<branch>&per_page=1" --jq '.workflow_runs[0] | {id, status, conclusion, head_sha}'`
   （雲端 session 的 GitHub proxy 擋 GraphQL，`gh pr` / `gh issue` 不能用，一律用 `gh api` 打 REST）
3. 看哪一步失敗：`gh api 'repos/{owner}/{repo}/actions/runs/<id>/jobs' --jq '.jobs[] | {name, conclusion, steps: [.steps[] | select(.conclusion=="failure") | .name]}'`
4. 不論成功或失敗，run 結束後都讀 media branch（每次會被覆蓋，先核對 `info.txt` 的 commit 是不是自己這次的）：

   ```bash
   git fetch origin media
   rm -rf /tmp/media && mkdir -p /tmp/media
   git archive origin/media | tar -x -C /tmp/media
   ```

   - `/tmp/media/frames/*.png`：每 2 秒一張影格，用 Read 看圖確認畫面
   - `/tmp/media/screenshots/*.png`：測試在關鍵畫面存的截圖（半解析度），橫屏的細節看這裡
   - `/tmp/media/screenshots/release-*.png`：錄影結束後覆蓋安裝 release 版，三種錶面各一張，再加上螢幕保護程式（`release-4-dream.png`，全解析度）；
     `release-smoke.txt` 是啟動時間，`logcat-release.txt` 有 `FATAL EXCEPTION` 時 job 會失敗
   - `/tmp/media/info.txt`：branch、commit、api level、run 網址、job status
   - `/tmp/media/gradle-build.txt`：CI 的 Gradle build 輸出
   - `/tmp/media/instrument.txt`、`logcat.txt`：測試輸出與 log
   - `/tmp/media/demo.mp4`：壓縮後的影片（給使用者看）
   - `/tmp/media/demo.gif`：README 用的動圖預覽（寬 300px、10 fps）

完成後告訴使用者：影片在 media branch 的 `demo.mp4`，或該次 Actions run 的 `android-demo` artifact。

`main` 的錄影（API 24）成功時，最後一步會把 release APK 和 `demo.mp4` 發佈到 tag `latest` 的 GitHub Release（刪掉重建，固定檔名
`clock-standby.apk`、`demo.mp4`），README 的下載連結用 `../../releases/latest/download/…`。
其他 branch 和其他 API level 不會發佈；只改文件或 workflow 合併到 `main` 也不會觸發，要更新 Release 就手動觸發 `main` 的錄影（`-f ref=main`）。

看完 frames 確認畫面正常後，把 `/tmp/media/demo.mp4` 複製到 session 的 scratchpad 目錄，
用 SendUserFile（`display: render`）直接傳給使用者，讓使用者不用 pull 就能播放。
傳之前先核對 `info.txt` 的 commit 是這次的。

## README 截圖與文件

- README 的截圖放在 `docs/screenshots/`，展示影片是 `docs/demo.mp4`；media branch 會被覆蓋，README 不要直接連過去
- GitHub README 不能內嵌播放 repo 裡的 mp4，README 顯示動圖 `docs/demo.gif`，點了連到 `docs/demo.mp4`
- 畫面有改、使用者要更新 README 時：等這次的錄影成功、看過截圖確認正常，再執行 `bash scripts/update-screenshots.sh`，
  把 `docs/` 的變更和其他文件一起 commit。script 會擋掉失敗的錄影，以及錄影之後 App 又改過的情況
- `scripts/update-screenshots.sh` 用檔名對應 `DemoRecordingTest` 的 `screenshot("…")`，改截圖名稱時兩邊一起改
- claude.ai/code 的環境設定步驟寫在 `docs/cloud-setup.md`，改了 `scripts/cloud-setup.sh` 或網路設定要一起更新
