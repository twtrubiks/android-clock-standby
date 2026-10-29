package com.twtrubiks.clockstandby

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 指針的角度（度），12 點方向為 0，順時針遞增。 */
data class HandAngles(val hour: Float, val minute: Float, val second: Float)

/** 防烙印用的位移方向，x / y 各為 -1、0、1，畫面再乘上實際距離。 */
data class BurnInShift(val x: Int, val y: Int)

/** 與 Android 無關的時鐘計算，方便在 JVM 上做單元測試。 */
object ClockMath {

    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.TAIWAN)
    private val numericDateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

    /** 沒在充電且電量在這個百分比以下（含）時顯示成紅色。 */
    const val LOW_BATTERY_PERCENT = 20

    /** 時鐘大小：1 代表塞滿可用空間，使用者可以用兩指在這個範圍內縮放。 */
    const val MIN_CLOCK_SCALE = 0.5f
    const val MAX_CLOCK_SCALE = 1f
    const val DEFAULT_CLOCK_SCALE = 0.9f

    // 中心 → 右 → 右下 → 下 → … 繞一圈，每分鐘換一格
    private val burnInPattern = listOf(
        BurnInShift(0, 0),
        BurnInShift(1, 0),
        BurnInShift(1, 1),
        BurnInShift(0, 1),
        BurnInShift(-1, 1),
        BurnInShift(-1, 0),
        BurnInShift(-1, -1),
        BurnInShift(0, -1),
        BurnInShift(1, -1),
    )

    /** 時針、分針會跟著秒數慢慢前進，不會每分鐘才跳一格。 */
    fun handAngles(time: LocalTime): HandAngles {
        val seconds = time.second + time.nano / 1_000_000_000f
        val minutes = time.minute + seconds / 60f
        val hours = time.hour % 12 + minutes / 60f
        return HandAngles(hour = hours * 30f, minute = minutes * 6f, second = seconds * 6f)
    }

    /** 12 小時制時 0 點顯示成 12。 */
    fun displayHour(hour: Int, use24Hour: Boolean): Int = when {
        use24Hour -> hour
        hour % 12 == 0 -> 12
        else -> hour % 12
    }

    fun periodLabel(hour: Int): String = if (hour < 12) "上午" else "下午"

    fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

    fun formatDate(date: LocalDate): String = date.format(dateFormatter)

    /** 橫屏用的數字日期，例如 2026/10/28。 */
    fun formatNumericDate(date: LocalDate): String = date.format(numericDateFormatter)

    /** 給螢幕閱讀器的時間描述，只到分鐘，避免每秒都送出無障礙事件。 */
    fun spokenTime(time: LocalTime, use24Hour: Boolean): String {
        val hour = displayHour(time.hour, use24Hour)
        val clock = "$hour:${twoDigits(time.minute)}"
        return if (use24Hour) "現在時間 $clock" else "現在時間 ${periodLabel(time.hour)} $clock"
    }

    /**
     * 秒針從目前角度走到 [second] 秒要轉的角度。只有剛好往前一格才跑動畫，
     * 其他情況（剛顯示、從背景回來、時間被調整）回傳 null，直接跳到正確位置。
     */
    fun secondTickStep(currentAngle: Float, second: Int): Float? {
        val step = ((second * 6f - currentAngle) % 360f + 360f) % 360f
        return if (step > 0.5f && step < 6.5f) step else null
    }

    /**
     * 翻頁動畫：進度 0～1，前半段舊數字的上半張從 0° 往前翻到 -90°，
     * 後半段新數字的下半張從 90° 翻回 0°。翻到 ±90° 時是側面，看不到。
     */
    fun flipTopAngle(progress: Float): Float = -180f * progress.coerceIn(0f, 0.5f)

    fun flipBottomAngle(progress: Float): Float = 180f * (1f - progress.coerceIn(0.5f, 1f))

    /** 翻動中的那片在翻到一半時最暗，模擬光線照不到。 */
    fun flipShade(progress: Float): Float = MAX_FLIP_SHADE * (1f - abs(progress.coerceIn(0f, 1f) - 0.5f) * 2f)

    private const val MAX_FLIP_SHADE = 0.45f

    fun burnInShift(minuteOfDay: Int): BurnInShift =
        burnInPattern[Math.floorMod(minuteOfDay, burnInPattern.size)]

    fun isLowBattery(percent: Int, charging: Boolean): Boolean = !charging && percent <= LOW_BATTERY_PERCENT

    fun clampClockScale(scale: Float): Float = scale.coerceIn(MIN_CLOCK_SCALE, MAX_CLOCK_SCALE)

    fun clockScalePercent(scale: Float): Int = (scale * 100).roundToInt()

    /** 電量百分比；系統沒給有效數值時回傳 null。 */
    fun batteryPercent(level: Int, scale: Int): Int? {
        if (level < 0 || scale <= 0) return null
        return (level * 100f / scale).toInt().coerceIn(0, 100)
    }
}
