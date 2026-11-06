# 發佈正式版

正式版由 GitHub Actions 的 [`release.yml`](../.github/workflows/release.yml) 發佈：push `v*` tag（例如 `v1.0.0`）時，
用正式金鑰打包 release APK，建立 GitHub Release。正式版都用同一把金鑰簽，手機上可以直接覆蓋升級，設定會保留。

## 三種 APK

| | applicationId | 簽章 | 用途 |
| --- | --- | --- | --- |
| debug | `com.twtrubiks.clockstandby` | debug 金鑰 | 開發、錄影 |
| preview | `com.twtrubiks.clockstandby.preview` | debug 金鑰 | 試用開發中的版本，名稱是「時鐘待機（預覽）」，可以和正式版同時安裝 |
| release | `com.twtrubiks.clockstandby` | 正式金鑰 | GitHub Release 發佈的正式版 |

- preview 和 release 一樣經過 R8 壓縮，只差在簽章和 applicationId
- 正式金鑰只有 `release.yml` 拿得到；其他地方（雲端 VM、錄影 workflow）打包 release 時用 debug 金鑰簽，
  錄影的冒煙測試才能覆蓋安裝在 debug 版上
- debug 金鑰每台機器、每個雲端 session 都不一樣，換 session 後拿到的預覽版要先移除舊的預覽版再裝

## 一次性設定：正式金鑰

在自己的電腦上做（不要在雲端 session 裡做），需要 JDK 的 `keytool` 和 [`gh`](https://cli.github.com/)。

1. 產生金鑰。alias 一定要用 `clock-standby`（`release.yml` 寫死這個值），名字、組織等資料隨意填：

   ```bash
   keytool -genkeypair -v -keystore clock-standby-release.jks -alias clock-standby \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **備份**：把 `clock-standby-release.jks` 和密碼一起存到密碼管理器或其他安全的地方。
   金鑰遺失就發不出能覆蓋安裝的新版，手機上只能移除重裝、設定會被清掉。
   `.jks` 不要 commit（`.gitignore` 已經擋掉）。

3. 存進 GitHub Secrets（在 repo 目錄下執行，第二行會提示輸入密碼）：

   ```bash
   base64 -w0 clock-standby-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
   gh secret set RELEASE_KEYSTORE_PASSWORD
   ```

   也可以到 repo 的 Settings > Secrets and variables > Actions 手動新增。

## 發佈新版本

1. 改 `app/build.gradle.kts`：`versionCode` 加 1，`versionName` 照 semver 改（修 bug 改第三碼、加功能改第二碼）
2. commit（例如 `chore: release v1.1.0`）並 push 到 `main`，等這次的錄影通過（包含 release 版的冒煙測試）
3. 打 tag 並 push：

   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

4. `release.yml` 會檢查 tag 和 `versionName` 一致、跑單元測試、用正式金鑰打包並確認不是 debug 簽章，
   再建立 Release，附上 `clock-standby.apk` 和 SHA-256，更新說明列出上一版之後的 commit

APK 檔名固定是 `clock-standby.apk`，README 的 `releases/latest/download/clock-standby.apk` 會自動指向最新版。

workflow 失敗或 tag 打錯時，修好之後刪掉 tag 重打：

```bash
gh release delete v1.1.0 --cleanup-tag --yes   # Release 已經建立時；沒有的話改用 git push origin --delete v1.1.0
git tag -d v1.1.0
```

## 從舊版換到正式版

v1.0.0 之前 Release 頁面和對話裡給的 APK，都是用 debug 金鑰簽的 `com.twtrubiks.clockstandby`，和正式版簽章不同，
要先移除舊版再安裝（設定會被清掉，螢幕保護程式也要重新選）。之後的正式版都能直接覆蓋升級。
