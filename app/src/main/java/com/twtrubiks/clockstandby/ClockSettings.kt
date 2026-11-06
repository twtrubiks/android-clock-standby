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
    /** 低亮度的紅色配色，螢幕亮度預設也調到最低。 */
    val nightMode: Boolean = false,
    /** 在畫面左右兩側上下滑動調整亮度。關掉時白天跟著系統亮度，夜間用預設的低亮度。 */
    val brightnessGesture: Boolean = true,
    /** 白天用滑動調過的亮度等級（0～1，畫面上顯示的百分比）；null 代表沒調過，跟著系統亮度。 */
    val dayBrightness: Float? = null,
    /** 夜間模式用滑動調過的亮度等級；null 代表沒調過，用預設的低亮度。 */
    val nightBrightness: Float? = null,
) {
    /** 目前（白天或夜間）要用的亮度等級；關掉滑動調整或還沒調過時是 null。 */
    val brightnessLevel: Float?
        get() = when {
            !brightnessGesture -> null
            nightMode -> nightBrightness
            else -> dayBrightness
        }

    /** 記住目前（白天或夜間）的亮度等級。 */
    fun withBrightnessLevel(level: Float): ClockSettings =
        if (nightMode) copy(nightBrightness = level) else copy(dayBrightness = level)

    /** 關掉滑動調整時一起清掉調過的亮度，再打開時從系統亮度（夜間是預設低亮度）重新開始。 */
    fun withBrightnessGesture(enabled: Boolean): ClockSettings =
        if (enabled) copy(brightnessGesture = true) else copy(brightnessGesture = false, dayBrightness = null, nightBrightness = null)
}

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
        brightnessGesture = prefs.getBoolean(KEY_BRIGHTNESS_GESTURE, true),
        dayBrightness = prefs.getBrightness(KEY_DAY_BRIGHTNESS),
        nightBrightness = prefs.getBrightness(KEY_NIGHT_BRIGHTNESS),
    )

    fun save(settings: ClockSettings) {
        val current = load()
        prefs.edit {
            if (settings.use24Hour != current.use24Hour) putBoolean(KEY_USE_24_HOUR, settings.use24Hour)
            if (settings.showSeconds != current.showSeconds) putBoolean(KEY_SHOW_SECONDS, settings.showSeconds)
            if (settings.clockScale != current.clockScale) putFloat(KEY_CLOCK_SCALE, settings.clockScale)
            if (settings.face != current.face) putInt(KEY_FACE, settings.face.ordinal)
            if (settings.nightMode != current.nightMode) putBoolean(KEY_NIGHT_MODE, settings.nightMode)
            if (settings.brightnessGesture != current.brightnessGesture) {
                putBoolean(KEY_BRIGHTNESS_GESTURE, settings.brightnessGesture)
            }
            if (settings.dayBrightness != current.dayBrightness) putBrightness(KEY_DAY_BRIGHTNESS, settings.dayBrightness)
            if (settings.nightBrightness != current.nightBrightness) {
                putBrightness(KEY_NIGHT_BRIGHTNESS, settings.nightBrightness)
            }
        }
    }

    // 沒調過的亮度不存，用 contains 分辨
    private fun SharedPreferences.getBrightness(key: String): Float? =
        if (contains(key)) ClockMath.clampBrightnessLevel(getFloat(key, 1f)) else null

    private fun SharedPreferences.Editor.putBrightness(key: String, level: Float?) {
        if (level == null) remove(key) else putFloat(key, level)
    }

    companion object {
        const val KEY_USE_24_HOUR = "use_24_hour"
        const val KEY_SHOW_SECONDS = "show_seconds"
        const val KEY_CLOCK_SCALE = "clock_scale"
        const val KEY_FACE = "face"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_BRIGHTNESS_GESTURE = "brightness_gesture"
        const val KEY_DAY_BRIGHTNESS = "day_brightness"
        const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
    }
}
