package com.twtrubiks.clockstandby

import android.service.dreams.DreamService
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * 充電待機：系統的螢幕保護程式（Daydream）。
 * 使用者在「設定 > 顯示 > 螢幕保護程式」選擇「時鐘待機」並設定充電時啟動後，
 * 由系統在充電且閒置時自動開啟，App 不需要常駐服務，也不用自己喚醒螢幕。
 * 錶面、時制、秒數、大小和夜間模式都沿用 App 裡最後的設定；碰一下螢幕就結束。
 */
class ClockDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    // ComposeView 需要 LifecycleOwner 和 SavedStateRegistryOwner，DreamService 本身沒有，要自己提供
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        // 一定要在 ON_CREATE 之前呼叫
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        val settings = ClockSettingsStore(prefs, systemUse24Hour = DateFormat.is24HourFormat(this)).load()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ClockDreamService)
            setViewTreeSavedStateRegistryOwner(this@ClockDreamService)
            setContent { DreamClockScreen(settings) }
        }
        setContentView(view)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

/** 螢幕保護程式的畫面：和待機畫面同一套版面與防烙印，但沒有控制列，也不能滑動換錶面。 */
@Composable
fun DreamClockScreen(settings: ClockSettings) {
    CompositionLocalProvider(LocalClockSettings provides settings) {
        ClockStandbyTheme {
            StandbyWindowEffect(night = settings.nightMode)
            val colors = if (settings.nightMode) NightClockColors else DayClockColors
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                val landscape = maxWidth > maxHeight
                StandbyFrame(colors = colors, landscape = landscape, dimStatus = settings.nightMode) {
                    ClockFaceContent(
                        face = settings.face,
                        colors = colors,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = if (landscape) 8.dp else 24.dp),
                    )
                }
            }
        }
    }
}
