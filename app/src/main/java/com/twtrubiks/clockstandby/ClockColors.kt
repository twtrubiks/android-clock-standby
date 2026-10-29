package com.twtrubiks.clockstandby

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/** 錶面用的配色；夜間模式整組換成低亮度的紅色，不影響睡眠。 */
@Immutable
data class ClockColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    /** 翻頁卡片的上半張比下半張亮一點，像光從上面照下來。 */
    val cardTop: Color,
    val cardBottom: Color,
)

val DayClockColors = ClockColors(
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    primary = Color.White,
    secondary = Color(0xFF8E8E93),
    accent = Color(0xFFFF9F0A),
    cardTop = Color(0xFF2A2A2D),
    cardBottom = Color(0xFF1E1E21),
)

val NightClockColors = ClockColors(
    background = Color.Black,
    surface = Color(0xFF1A0504),
    primary = Color(0xFFC62828),
    secondary = Color(0xFF6D1A16),
    accent = Color(0xFFFF3B30),
    cardTop = Color(0xFF230806),
    cardBottom = Color(0xFF180403),
)

@Composable
fun animateClockColors(night: Boolean): ClockColors {
    val target = if (night) NightClockColors else DayClockColors
    val spec = tween<Color>(durationMillis = 800)
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val cardTop by animateColorAsState(target.cardTop, spec, label = "cardTop")
    val cardBottom by animateColorAsState(target.cardBottom, spec, label = "cardBottom")
    return ClockColors(target.background, surface, primary, secondary, accent, cardTop, cardBottom)
}

@Composable
fun ClockStandbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DayClockColors.accent,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = Color.Black,
        ),
        content = content,
    )
}
