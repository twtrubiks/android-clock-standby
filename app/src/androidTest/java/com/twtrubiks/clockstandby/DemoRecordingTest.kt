package com.twtrubiks.clockstandby

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * 錄影用的展示流程：介紹頁 → 待機時鐘（控制列預設隱藏）→ 滑動 / 點指示點換錶面 → 設定面板切 24 小時制
 * → 夜間模式 → 橫屏 → 接上充電器 → 關掉秒數 → 控制列自動收起 → 兩指放大時鐘。
 * CI 只跑這個 class，`scripts/ci/record.sh` 會在執行期間錄下整個螢幕。
 */
@RunWith(AndroidJUnit4::class)
class DemoRecordingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    // record.sh 會把這個資料夾 pull 回來，放進 media branch 的 screenshots/
    private val screenshotDir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")

    // 控制列右邊的設定按鈕；看得到它就代表控制列正在顯示
    private val settingsButton = By.desc("設定")

    @Test
    fun demo() {
        // 清掉設定（看過介紹、24 小時制、秒數、時鐘大小、錶面、夜間模式），確保每次都從介紹頁和預設值開始
        prefs().edit().clear().commit()
        screenshotDir.deleteRecursively()
        screenshotDir.mkdirs()

        // 模擬器預設 100% 充電中，改成 76% 沒插電，後面再「接上充電器」
        device.executeShellCommand("dumpsys battery unplug")
        device.executeShellCommand("dumpsys battery set status ${BatteryManager.BATTERY_STATUS_DISCHARGING}")
        device.executeShellCommand("dumpsys battery set level 76")

        // record.sh 已經處理過開機時的 ANR，這裡再保險一次
        dismissAnrDialog()

        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                // 介紹頁 1：時鐘待機（CI 模擬器剛開機，第一個畫面可能要等比較久）
                waitFor("時鐘待機", timeout = 30_000)
                // ActivityScenario.launch 在 CI 上要等十幾秒，這段時間第一頁已經一直顯示，不用停太久
                pause(1500)
                screenshot("01-intro")

                // 介紹頁 2：三種錶面（預覽每 2 秒換一種）
                waitFor("下一步").click()
                waitFor("三種錶面")
                pause(3000)

                // 介紹頁 3：用滑的換頁
                swipeLeft(device.displayHeight * 2 / 5)
                waitFor("夜間模式")
                pause(2000)

                // 進入待機時鐘：預設數位錶面，畫面上只有時鐘、日期和電量，控制列先藏起來
                waitFor("開始使用").click()
                waitForDesc("電量 76%")
                assertFalse("控制列應該預設隱藏", device.hasObject(settingsButton))
                pause(2000)
                screenshot("02-digital-12h")

                // 左滑換到指針錶面：下方會短暫出現指示點，再點指示點換到翻頁錶面
                swipeLeft(device.displayHeight / 2)
                assertTrue("指示點應該停在「指針」", isFaceSelected("指針"))
                pause(2000)
                screenshot("03-analog")
                selectFace("翻頁")
                pause(2500)
                screenshot("04-flip")

                // 打開設定面板，切成 24 小時制
                selectFace("數位")
                toggleSetting("24 小時制", checked = true)
                screenshot("05-settings-panel")
                closeSettings()
                pause(1500)
                screenshot("06-portrait-24h")

                // 夜間模式，再轉成橫屏：日期變成 2026/10/28 這種格式
                toggleSetting("夜間模式", checked = true)
                closeSettings()
                selectFace("指針")
                pause(1000)
                device.setOrientationLandscape()
                // 時鐘轉向前就找得到，改等橫屏才有的數字日期，再停一下等系統的轉向動畫跑完
                // （Android 7 模擬器轉向比較慢，只等時鐘會截到轉到一半的畫面）
                waitFor(ClockMath.formatNumericDate(LocalDate.now()))
                pause(2500)
                screenshot("07-landscape-night")
                selectFace("翻頁")
                pause(2500)

                // 關掉夜間模式、接上充電器：電量旁邊出現閃電
                toggleSetting("夜間模式", checked = false)
                closeSettings()
                device.executeShellCommand("dumpsys battery set ac 1")
                device.executeShellCommand("dumpsys battery set status ${BatteryManager.BATTERY_STATUS_CHARGING}")
                waitForDesc("電量 76%，充電中")
                selectFace("數位")
                pause(2000)
                screenshot("08-landscape-charging")

                // 關掉秒數，只留下時和分
                toggleSetting("顯示秒數", checked = false)
                closeSettings()

                // 不操作，控制列幾秒後自動收起，只留下時鐘
                assertTrue("控制列沒有自動收起", device.wait(Until.gone(settingsButton), 10_000))
                pause(1500)

                // 兩指放大時鐘，放開後會記住新的大小
                val clock = requireNotNull(device.findObject(By.descStartsWith("現在時間"))) { "畫面上找不到時鐘" }
                clock.pinchOpen(0.8f)
                pause(2000)
                assertTrue("時鐘沒有放大", savedClockScale() > ClockMath.DEFAULT_CLOCK_SCALE)
                screenshot("09-landscape-zoomed")

                // 點一下時鐘叫回控制列
                device.click(device.displayWidth / 2, device.displayHeight / 2)
                assertTrue("控制列沒有出現", device.wait(Until.hasObject(settingsButton), 5_000))
                pause(1000)

                // 轉回直屏
                device.setOrientationPortrait()
                waitForClock()
                pause(2000)
            }
        } finally {
            device.unfreezeRotation()
            device.executeShellCommand("dumpsys battery reset")
        }
    }

    private fun waitFor(text: String, timeout: Long = 10_000): UiObject2 {
        val selector = By.text(text)
        device.wait(Until.findObject(selector), timeout)?.let { return it }
        // 找不到時可能被系統 ANR 對話框擋住，關掉再找一次
        dismissAnrDialog()
        return requireNotNull(device.wait(Until.findObject(selector), timeout)) { "畫面上找不到「$text」" }
    }

    private fun prefs() =
        instrumentation.targetContext.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)

    private fun savedClockScale() =
        prefs().getFloat(ClockSettingsStore.KEY_CLOCK_SCALE, ClockMath.DEFAULT_CLOCK_SCALE)

    private fun waitForDesc(description: String) {
        assertTrue("畫面上找不到「$description」", device.wait(Until.hasObject(By.desc(description)), 10_000))
    }

    // 系統程式 ANR 時按「Close app」讓它重開；按 Wait 的話 5 秒後會再跳出來。
    // CI 模擬器上每次查詢要 1～2 秒，只在必要時呼叫
    private fun dismissAnrDialog() {
        device.findObject(By.res("android", "aerr_close"))?.click()
    }

    private fun waitForClock() {
        assertTrue("畫面上找不到時鐘", device.wait(Until.hasObject(By.descStartsWith("現在時間")), 10_000))
    }

    // 控制列沒有操作幾秒後會自動收起；收起來時點一下時鐘叫出來。
    // 點時鐘是切換顯示 / 隱藏：查詢時控制列剛好還在（例如正要自動收起），這一下反而會把它關掉，所以沒出現就再點一次
    private fun showControls() {
        repeat(2) {
            if (device.hasObject(settingsButton)) return
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            if (device.wait(Until.hasObject(settingsButton), 5_000)) return
        }
        throw AssertionError("控制列沒有出現")
    }

    // CI 模擬器上每次查詢要 1～2 秒，控制列可能剛好在點之前收起來，收起來就再叫出來重點一次
    private fun clickControl(selector: BySelector) {
        repeat(3) {
            showControls()
            try {
                device.findObject(selector)?.let {
                    it.click()
                    return
                }
            } catch (e: StaleObjectException) {
                // 找到之後才收起來，重來一次
            }
        }
        throw AssertionError("點不到控制列上的 $selector")
    }

    // 錶面指示點是 Tab（isSelected），名稱放在無障礙描述
    private fun isFaceSelected(label: String): Boolean {
        showControls()
        return hasState({ By.desc(label) }, { selected(true) })
    }

    /**
     * 等到有名稱為 [label] 的元件處於 [state]。Compose 會把整塊可以點的元件（錶面指示點、設定開關列）
     * 拆成父子兩個無障礙節點：選取、開關狀態在父節點，文字、描述在子節點（讀螢幕的服務會合起來唸），
     * 所以名稱在自己身上或子節點上都算。
     */
    private fun hasState(
        label: () -> BySelector,
        state: BySelector.() -> BySelector,
        timeout: Long = 3_000,
    ): Boolean {
        val onSelf = label().state()
        val onParent = By.hasDescendant(label()).state()
        val deadline = SystemClock.uptimeMillis() + timeout
        while (true) {
            if (device.hasObject(onParent) || device.hasObject(onSelf)) return true
            if (SystemClock.uptimeMillis() > deadline) return false
            SystemClock.sleep(200)
        }
    }

    private fun selectFace(label: String) {
        repeat(3) {
            clickControl(By.desc(label))
            pause(800)
            if (isFaceSelected(label)) return
        }
        throw AssertionError("沒辦法切換到「$label」錶面")
    }

    // 設定面板裡的開關是 Switch（isChecked），整列的文字就是開關名稱
    private fun toggleSetting(label: String, checked: Boolean) {
        openSettings()
        waitFor(label).click()
        pause(600)
        assertTrue("「$label」的開關狀態不對", hasState({ By.text(label) }, { checked(checked) }))
    }

    private fun openSettings() {
        if (device.hasObject(By.text("顯示秒數"))) return
        clickControl(settingsButton)
        waitFor("顯示秒數")
        pause(600)
    }

    // 只在面板打開時呼叫：面板沒開時按返回鍵會離開 App
    private fun closeSettings() {
        device.pressBack()
        assertTrue("設定面板沒有關閉", device.wait(Until.gone(By.text("顯示秒數")), 5_000))
    }

    // 每一步都是一個同步注入的觸控事件，CI 模擬器上 25 步要 10 秒以上，10 步就夠翻頁
    private fun swipeLeft(y: Int) {
        val width = device.displayWidth
        device.swipe(width * 4 / 5, y, width / 5, y, 10)
        pause(800)
    }

    // 橫屏時影片裡的畫面會被縮小，另外存一張截圖方便確認細節（縮成一半就夠清楚）。
    // scripts/update-screenshots.sh 會挑其中幾張放進 README，改名字要一起改
    private fun screenshot(name: String) {
        device.takeScreenshot(File(screenshotDir, "$name.png"), 0.5f, 90)
    }

    private fun pause(ms: Long = 1200) {
        device.waitForIdle()
        SystemClock.sleep(ms)
    }
}
