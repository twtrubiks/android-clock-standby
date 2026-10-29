package com.twtrubiks.clockstandby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ClockMathTest {

    @Test
    fun handAngles_onTheHour() {
        val angles = ClockMath.handAngles(LocalTime.of(3, 0))
        assertEquals(90f, angles.hour, 0.001f)
        assertEquals(0f, angles.minute, 0.001f)
        assertEquals(0f, angles.second, 0.001f)
    }

    @Test
    fun handAngles_includeFractionalProgress() {
        val angles = ClockMath.handAngles(LocalTime.of(10, 30, 15, 500_000_000))
        assertEquals(315.1292f, angles.hour, 0.001f)
        assertEquals(181.55f, angles.minute, 0.001f)
        assertEquals(93f, angles.second, 0.001f)
    }

    @Test
    fun handAngles_wrapAfterNoon() {
        assertEquals(90f, ClockMath.handAngles(LocalTime.of(15, 0)).hour, 0.001f)
        assertEquals(0f, ClockMath.handAngles(LocalTime.MIDNIGHT).hour, 0.001f)
    }

    @Test
    fun displayHour_followsClockFormat() {
        assertEquals(12, ClockMath.displayHour(0, use24Hour = false))
        assertEquals(12, ClockMath.displayHour(12, use24Hour = false))
        assertEquals(1, ClockMath.displayHour(13, use24Hour = false))
        assertEquals(0, ClockMath.displayHour(0, use24Hour = true))
        assertEquals(23, ClockMath.displayHour(23, use24Hour = true))
    }

    @Test
    fun periodLabel_splitsAtNoon() {
        assertEquals("上午", ClockMath.periodLabel(11))
        assertEquals("下午", ClockMath.periodLabel(12))
    }

    @Test
    fun formatDate_usesTraditionalChinese() {
        assertEquals("10月27日 星期二", ClockMath.formatDate(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun formatNumericDate_padsMonthAndDay() {
        assertEquals("2026/10/28", ClockMath.formatNumericDate(LocalDate.of(2026, 10, 28)))
        assertEquals("2027/01/05", ClockMath.formatNumericDate(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun spokenTime_stopsAtMinutes() {
        assertEquals("現在時間 下午 1:05", ClockMath.spokenTime(LocalTime.of(13, 5, 42), use24Hour = false))
        assertEquals("現在時間 13:05", ClockMath.spokenTime(LocalTime.of(13, 5, 42), use24Hour = true))
    }

    @Test
    fun burnInShift_staysInBoundsAndMovesEveryMinute() {
        assertEquals(BurnInShift(0, 0), ClockMath.burnInShift(0))
        for (minute in 0 until 24 * 60) {
            val shift = ClockMath.burnInShift(minute)
            assertTrue(shift.x in -1..1 && shift.y in -1..1)
            assertNotEquals(shift, ClockMath.burnInShift(minute + 1))
        }
    }

    @Test
    fun isLowBattery_onlyWhenDischargingAtOrBelowThreshold() {
        assertTrue(ClockMath.isLowBattery(percent = 20, charging = false))
        assertFalse(ClockMath.isLowBattery(percent = 21, charging = false))
        assertFalse(ClockMath.isLowBattery(percent = 5, charging = true))
    }

    @Test
    fun clampClockScale_staysInRange() {
        assertEquals(0.5f, ClockMath.clampClockScale(0.2f), 0.001f)
        assertEquals(1f, ClockMath.clampClockScale(1.7f), 0.001f)
        assertEquals(0.9f, ClockMath.clampClockScale(ClockMath.DEFAULT_CLOCK_SCALE), 0.001f)
        assertEquals(90, ClockMath.clockScalePercent(0.9f))
        assertEquals(100, ClockMath.clockScalePercent(0.999f))
    }

    @Test
    fun secondTickStep_onlyAnimatesOneSecondForward() {
        assertEquals(6f, ClockMath.secondTickStep(currentAngle = 0f, second = 1)!!, 0.001f)
        // 59 → 0 秒往前走一格，不是倒轉一圈
        assertEquals(6f, ClockMath.secondTickStep(currentAngle = 354f, second = 0)!!, 0.001f)
        // 同一秒、往回走、跳過好幾秒都直接跳到正確位置
        assertNull(ClockMath.secondTickStep(currentAngle = 6f, second = 1))
        assertNull(ClockMath.secondTickStep(currentAngle = 12f, second = 1))
        assertNull(ClockMath.secondTickStep(currentAngle = 0f, second = 30))
    }

    @Test
    fun flipAngles_topFlapFirstThenBottomFlap() {
        assertEquals(0f, ClockMath.flipTopAngle(0f), 0.001f)
        assertEquals(-45f, ClockMath.flipTopAngle(0.25f), 0.001f)
        assertEquals(-90f, ClockMath.flipTopAngle(0.5f), 0.001f)
        assertEquals(-90f, ClockMath.flipTopAngle(1f), 0.001f)

        assertEquals(90f, ClockMath.flipBottomAngle(0f), 0.001f)
        assertEquals(90f, ClockMath.flipBottomAngle(0.5f), 0.001f)
        assertEquals(45f, ClockMath.flipBottomAngle(0.75f), 0.001f)
        assertEquals(0f, ClockMath.flipBottomAngle(1f), 0.001f)
    }

    @Test
    fun flipShade_darkestHalfway() {
        assertEquals(0f, ClockMath.flipShade(0f), 0.001f)
        assertEquals(0f, ClockMath.flipShade(1f), 0.001f)
        assertTrue(ClockMath.flipShade(0.5f) > ClockMath.flipShade(0.25f))
        assertEquals(ClockMath.flipShade(0.25f), ClockMath.flipShade(0.75f), 0.001f)
    }

    @Test
    fun batteryPercent_handlesInvalidValues() {
        assertEquals(50, ClockMath.batteryPercent(level = 50, scale = 100))
        assertEquals(100, ClockMath.batteryPercent(level = 255, scale = 255))
        assertNull(ClockMath.batteryPercent(level = -1, scale = 100))
        assertNull(ClockMath.batteryPercent(level = 50, scale = 0))
    }
}
