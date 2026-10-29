#!/usr/bin/env bash
# 把 media branch 最新一次錄影的截圖、影片和動圖預覽複製到 docs/，給 README 用。
# 用法：bash scripts/update-screenshots.sh
# 只接受成功的錄影，而且錄影之後 App 沒有再改過（只改文件的 commit 不會觸發錄影，所以不要求 commit 相同）。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

git fetch -q origin media
git archive origin/media | tar -x -C "$tmp"

if ! grep -q '^job status: success' "$tmp/info.txt"; then
  echo "media branch 上這次的錄影沒有成功：" >&2
  cat "$tmp/info.txt" >&2
  exit 1
fi

recorded=$(sed -n 's/^commit: //p' "$tmp/info.txt")
if ! git cat-file -e "$recorded^{commit}" 2>/dev/null; then
  echo "本地沒有錄影用的 commit ${recorded:0:7}，先 fetch 那個 branch：" >&2
  cat "$tmp/info.txt" >&2
  exit 1
fi
# 和 workflow 自動觸發錄影的路徑相同
if ! git diff --quiet "$recorded" HEAD -- app gradle '*.gradle.kts' gradle.properties; then
  echo "media branch 是 ${recorded:0:7} 的錄影，之後 App 又改過，等新的錄影跑完再更新" >&2
  exit 1
fi

# README 用的檔名 ← DemoRecordingTest / record.sh 存的截圖
mkdir -p docs/screenshots
while read -r name source; do
  cp "$tmp/screenshots/$source" "docs/screenshots/$name"
done <<'EOF'
intro.png      01-intro.png
digital.png    02-digital-12h.png
analog.png     03-analog.png
flip.png       04-flip.png
settings.png   05-settings-panel.png
night.png      07-landscape-night.png
landscape.png  08-landscape-charging.png
dream.png      release-4-dream.png
EOF
cp "$tmp/demo.mp4" docs/demo.mp4
# README 的動圖預覽
cp "$tmp/demo.gif" docs/demo.gif

echo "已更新 docs/screenshots/、docs/demo.mp4 和 docs/demo.gif（${recorded:0:7} 的錄影）"
