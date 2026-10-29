package com.twtrubiks.clockstandby

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.core.content.edit

/** 使用者在待機畫面可以調整的設定；螢幕保護程式也照這些設定顯示。 */
@Immutable
data class ClockSettings(
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = true,
    /** 錶面佔可用空間的比例，用兩指縮放調整。 */
    val clockScale: Float = ClockMath.DEFAULT_CLOCK_SCALE,
    /** 最後選的錶面。 */
    val face: ClockFace = ClockFace.Digital,
    /** 低亮度的紅色配色，螢幕亮度也調到最低。 */
    val nightMode: Boolean = false,
)

/** 錶面從這裡讀時制、要不要顯示秒數、大小。 */
val LocalClockSettings = compositionLocalOf { ClockSettings() }

/**
 * 把 ClockSettings 存在 SharedPreferences。
 * 只寫入有改動的值：使用者沒切換過 24 小時制，就一直跟著系統設定。
 */
class ClockSettingsStore(private val prefs: SharedPreferences, private val systemUse24Hour: Boolean) {

    fun load() = ClockSettings(
        use24Hour = prefs.getBoolean(KEY_USE_24_HOUR, systemUse24Hour),
        showSeconds = prefs.getBoolean(KEY_SHOW_SECONDS, true),
        clockScale = prefs.getFloat(KEY_CLOCK_SCALE, ClockMath.DEFAULT_CLOCK_SCALE),
        // 存順序而不是名稱：release 版 R8 會改掉 enum 的名稱
        face = ClockFace.entries.getOrElse(prefs.getInt(KEY_FACE, 0)) { ClockFace.Digital },
        nightMode = prefs.getBoolean(KEY_NIGHT_MODE, false),
    )

    fun save(settings: ClockSettings) {
        val current = load()
        prefs.edit {
            if (settings.use24Hour != current.use24Hour) putBoolean(KEY_USE_24_HOUR, settings.use24Hour)
            if (settings.showSeconds != current.showSeconds) putBoolean(KEY_SHOW_SECONDS, settings.showSeconds)
            if (settings.clockScale != current.clockScale) putFloat(KEY_CLOCK_SCALE, settings.clockScale)
            if (settings.face != current.face) putInt(KEY_FACE, settings.face.ordinal)
            if (settings.nightMode != current.nightMode) putBoolean(KEY_NIGHT_MODE, settings.nightMode)
        }
    }

    companion object {
        const val KEY_USE_24_HOUR = "use_24_hour"
        const val KEY_SHOW_SECONDS = "show_seconds"
        const val KEY_CLOCK_SCALE = "clock_scale"
        const val KEY_FACE = "face"
        const val KEY_NIGHT_MODE = "night_mode"
    }
}
