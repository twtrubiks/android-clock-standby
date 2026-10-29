package com.twtrubiks.clockstandby

import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ClockFace(val label: String) {
    Digital("數位"),
    Analog("指針"),
    Flip("翻頁"),
}

@Composable
fun ClockFaceContent(face: ClockFace, colors: ClockColors, modifier: Modifier = Modifier) {
    when (face) {
        ClockFace.Digital -> DigitalClockFace(colors, modifier)
        ClockFace.Analog -> AnalogClockFace(colors, modifier)
        ClockFace.Flip -> FlipClockFace(colors, modifier)
    }
}

/**
 * 大字數位時鐘：冒號每秒閃一次，進度條走完一圈是一分鐘。
 * 橫向空間夠時把上午 / 下午和秒數放到時間右邊，讓數字盡量放大。
 */
@Composable
fun DigitalClockFace(colors: ClockColors, modifier: Modifier = Modifier) {
    val now by rememberTimeEverySecond()
    val settings = LocalClockSettings.current
    val use24Hour = settings.use24Hour
    val showSeconds = settings.showSeconds
    val time = now.toLocalTime()
    val colonAlpha by animateFloatAsState(
        targetValue = if (time.second % 2 == 0) 1f else 0.2f,
        animationSpec = tween(450),
        label = "colon",
    )
    // 每秒只動 300ms，其餘時間不重畫：待機省電，CI 模擬器上主執行緒也才有空閒
    val secondProgress by animateFloatAsState(
        // 不顯示秒數時固定不動，不用每秒跑動畫
        targetValue = if (showSeconds) (time.second + 1) / 60f else 0f,
        // 59 → 0 秒時直接歸零，不要倒著跑回去
        animationSpec = if (time.second == 0) snap() else tween(300),
        label = "seconds",
    )
    val period = if (use24Hour) null else ClockMath.periodLabel(time.hour)
    val seconds = ClockMath.twoDigits(time.second)

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = ClockMath.spokenTime(time, use24Hour)
        },
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth > maxHeight * 1.6f
        val sideColumn = period != null || showSeconds
        // 「00:00」約 2.55 個字寬、行高約 1.2 個字高。
        // 寬版：右邊那欄小字約 0.4 個字寬，進度條另佔 16dp；直版：上午 / 下午、秒數列各約 0.3 個字高
        val fit = if (wide) {
            val heightForTime = maxHeight - if (showSeconds) 16.dp else 0.dp
            min(maxWidth / if (sideColumn) 3f else 2.65f, heightForTime / 1.2f)
        } else {
            val rows = 1.2f + (if (period != null) 0.3f else 0f) + (if (showSeconds) 0.3f else 0f)
            min(maxWidth / 2.7f, maxHeight / rows)
        }
        val fontSize = with(LocalDensity.current) { (fit * settings.clockScale).toSp() }
        val digitStyle = TextStyle(
            color = colors.primary,
            fontSize = fontSize,
            fontWeight = FontWeight.Thin,
            fontFeatureSettings = "tnum",
        )
        val secondsStyle = TextStyle(
            color = colors.accent,
            fontSize = fontSize * if (wide) 0.2f else 0.22f,
            fontFeatureSettings = "tnum",
        )
        val clockText: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hour = ClockMath.twoDigits(ClockMath.displayHour(now.hour, use24Hour))
                Text(hour, style = digitStyle, softWrap = false)
                // 在 graphicsLayer 裡才讀透明度：閃爍動畫只改圖層屬性，不用重組也不用重畫文字
                Text(
                    ":",
                    style = digitStyle,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer { alpha = colonAlpha },
                )
                Text(ClockMath.twoDigits(now.minute), style = digitStyle, softWrap = false)
            }
        }

        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wide) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    clockText()
                    if (sideColumn) {
                        Spacer(Modifier.width(with(LocalDensity.current) { (fontSize * 0.08f).toDp() }))
                        Column {
                            period?.let { Text(it, color = colors.secondary, fontSize = fontSize * 0.16f) }
                            if (showSeconds) Text(seconds, style = secondsStyle)
                        }
                    }
                }
                if (showSeconds) {
                    Spacer(Modifier.height(8.dp))
                    SecondsProgressBar({ secondProgress }, colors, Modifier.fillMaxWidth())
                }
            } else {
                period?.let { Text(it, color = colors.secondary, fontSize = fontSize * 0.2f) }
                clockText()
                if (showSeconds) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SecondsProgressBar({ secondProgress }, colors, Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(seconds, style = secondsStyle)
                    }
                }
            }
        }
    }
}

/** 進度用 lambda 傳進來、在畫的時候才讀，動畫進行中只重畫這條，不用重組整個錶面。 */
@Composable
private fun SecondsProgressBar(progress: () -> Float, colors: ClockColors, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .height(4.dp)
            .clip(CircleShape),
    ) {
        drawRect(colors.surface)
        drawRect(colors.accent, size = size.copy(width = size.width * progress()))
    }
}

/**
 * 指針時鐘：秒針像石英錶一樣每秒跳一格，每次只動 [SECOND_TICK_MILLIS]，其餘時間不重畫，
 * 比跟著螢幕每個影格平滑轉動省電很多。
 * 錶盤（刻度、數字）和指針分開畫，時間更新時只重畫指針那一層。
 */
@Composable
fun AnalogClockFace(colors: ClockColors, modifier: Modifier = Modifier) {
    val settings = LocalClockSettings.current
    val use24Hour = settings.use24Hour
    val showSeconds = settings.showSeconds
    val time = rememberTimeEverySecond()
    val secondAngle = remember { Animatable(time.value.second * 6f) }
    if (showSeconds) {
        LaunchedEffect(time) {
            snapshotFlow { time.value.second }.collect { second ->
                val step = ClockMath.secondTickStep(secondAngle.value, second)
                if (step == null) {
                    secondAngle.snapTo(second * 6f)
                } else {
                    secondAngle.animateTo(
                        secondAngle.value + step,
                        tween(SECOND_TICK_MILLIS, easing = FastOutSlowInEasing),
                    )
                    // 59 → 0 秒時轉到 360°，再換回 0°，下一格才不會倒轉一圈
                    if (secondAngle.value >= 360f) secondAngle.snapTo(secondAngle.value - 360f)
                }
            }
        }
    }
    // 無障礙描述只到分鐘，換分鐘才重算字串，不用每次秒針更新都算
    val minute by remember(time) {
        derivedStateOf { time.value.toLocalTime().truncatedTo(ChronoUnit.MINUTES) }
    }
    val spoken = remember(minute, use24Hour) { ClockMath.spokenTime(minute, use24Hour) }
    // 12 個數字都要量，快取要比預設的 8 大
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        val dialSize = min(maxWidth, maxHeight) * settings.clockScale
        // 錶盤只在大小或顏色改變時重畫
        Canvas(Modifier.size(dialSize)) { drawDial(colors, textMeasurer) }
        // 指針放在自己的 graphics layer：時間只在這裡讀，更新時不用重組，也不會重畫錶盤
        Canvas(Modifier.size(dialSize).graphicsLayer()) {
            val radius = size.minDimension / 2f
            val angles = ClockMath.handAngles(time.value.toLocalTime())
            drawHand(angles.hour, length = radius * 0.5f, width = radius * 0.055f, color = colors.primary)
            drawHand(angles.minute, length = radius * 0.76f, width = radius * 0.035f, color = colors.primary)
            if (showSeconds) {
                drawHand(
                    secondAngle.value,
                    length = radius * 0.86f,
                    width = radius * 0.012f,
                    color = colors.accent,
                    tail = radius * 0.16f,
                )
            }
            drawCircle(colors.accent, radius * 0.045f)
            drawCircle(colors.surface, radius * 0.018f)
        }
    }
}

private fun DrawScope.drawDial(colors: ClockColors, textMeasurer: TextMeasurer) {
    val radius = size.minDimension / 2f
    drawCircle(colors.surface, radius)
    drawCircle(colors.secondary, radius, style = Stroke(width = radius * 0.012f))

    for (i in 0 until 60) {
        val isHour = i % 5 == 0
        rotate(i * 6f) {
            drawLine(
                color = if (isHour) colors.primary else colors.secondary,
                start = Offset(center.x, center.y - radius * 0.93f),
                end = Offset(center.x, center.y - radius * if (isHour) 0.81f else 0.87f),
                strokeWidth = radius * if (isHour) 0.025f else 0.01f,
                cap = StrokeCap.Round,
            )
        }
    }

    val numberStyle = TextStyle(fontSize = (radius * 0.15f).toSp(), fontWeight = FontWeight.Light)
    for (n in 1..12) {
        val layout = textMeasurer.measure(n.toString(), numberStyle)
        val angle = Math.toRadians(n * 30.0)
        val r = radius * 0.66f
        drawText(
            textLayoutResult = layout,
            color = colors.primary,
            topLeft = Offset(
                x = center.x + r * sin(angle).toFloat() - layout.size.width / 2f,
                y = center.y - r * cos(angle).toFloat() - layout.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawHand(angle: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
    rotate(angle) {
        drawLine(
            color = color,
            start = Offset(center.x, center.y + tail),
            end = Offset(center.x, center.y - length),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
}

/** 翻頁時鐘：每個數字變化時，新數字從上方翻入。橫向空間夠時排成一列。 */
@Composable
fun FlipClockFace(colors: ClockColors, modifier: Modifier = Modifier) {
    val now by rememberTimeEverySecond()
    val settings = LocalClockSettings.current
    val use24Hour = settings.use24Hour
    val showSeconds = settings.showSeconds
    val time = now.toLocalTime()
    val hour = ClockMath.twoDigits(ClockMath.displayHour(time.hour, use24Hour))
    val minute = ClockMath.twoDigits(time.minute)
    val second = ClockMath.twoDigits(time.second)
    val period = if (use24Hour) null else ClockMath.periodLabel(time.hour)
    val scale = settings.clockScale

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = ClockMath.spokenTime(time, use24Hour)
        },
        contentAlignment = Alignment.Center,
    ) {
        val gap = 12.dp
        if (maxWidth > maxHeight * 1.4f) {
            // 卡片高是寬的 1.2 倍；一列排兩張大卡，右邊再放秒數小卡（0.4 倍寬）或上午 / 下午
            val side = when {
                showSeconds -> 0.45f
                period != null -> 0.35f
                else -> 0f
            }
            val cardWidth = min((maxWidth - gap * 3) / (2f + side), maxHeight / 1.2f) * scale
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Bottom,
            ) {
                FlipCard(hour, cardWidth, colors)
                FlipCard(minute, cardWidth, colors)
                if (side > 0f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        period?.let { PeriodText(it, cardWidth, colors) }
                        if (showSeconds) FlipCard(second, cardWidth * 0.4f, colors, highlight = true)
                    }
                }
            }
        } else {
            // 上面兩張大卡（高 1.2 倍寬），下面一列放秒數卡（高 0.48 倍寬）或上午 / 下午
            val bottom = when {
                showSeconds -> 0.48f
                period != null -> 0.2f
                else -> 0f
            }
            val cardWidth = min((maxWidth - gap * 3) / 2, (maxHeight - gap) / (1.2f + bottom)) * scale
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    FlipCard(hour, cardWidth, colors)
                    FlipCard(minute, cardWidth, colors)
                }
                if (bottom > 0f) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        period?.let { PeriodText(it, cardWidth, colors) }
                        if (showSeconds) FlipCard(second, cardWidth * 0.4f, colors, highlight = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodText(text: String, cardWidth: Dp, colors: ClockColors) {
    val fontSize = with(LocalDensity.current) { (cardWidth * 0.14f).toSp() }
    Text(text, color = colors.secondary, fontSize = fontSize)
}

/**
 * 一張翻頁卡片：數字改變時，舊數字的上半張往前翻下來，接著新數字的下半張蓋上去。
 * 翻動的角度只在 graphicsLayer 和畫的時候讀，動畫進行中不用重組，只在開始和結束各重組一次。
 */
@Composable
private fun FlipCard(text: String, width: Dp, colors: ClockColors, highlight: Boolean = false) {
    val style = TextStyle(
        color = if (highlight) colors.accent else colors.primary,
        fontSize = with(LocalDensity.current) { (width * 0.72f).toSp() },
        fontFamily = FlipFontFamily,
        fontFeatureSettings = "tnum",
    )
    val corner = width * 0.08f
    // 秒數卡每秒都會翻，動畫要短，大部分時間才不用重畫
    val flipMillis = if (highlight) SECONDS_FLIP_MILLIS else FLIP_MILLIS

    // 翻頁前顯示的數字，動畫跑完才換成 text
    var shown by remember { mutableStateOf(text) }
    // text 一改變就從 0 開始：第一個影格就是翻頁的起點，不會先閃一下新數字
    val progress = remember(text) { Animatable(if (text == shown) 1f else 0f) }
    LaunchedEffect(progress) {
        progress.animateTo(1f, tween(flipMillis, easing = LinearEasing))
        shown = text
    }

    Box(Modifier.size(width, width * 1.2f)) {
        // 靜止的上半張已經是新數字，下半張在翻完之前還是舊數字
        FlipCardHalf(text, top = true, style, colors.cardTop, corner)
        FlipCardHalf(shown, top = false, style, colors.cardBottom, corner)
        if (shown != text) {
            FlipCardHalf(
                shown,
                top = true,
                style,
                colors.cardTop,
                corner,
                modifier = Modifier.flap { ClockMath.flipTopAngle(progress.value) },
                shade = { ClockMath.flipShade(progress.value) },
            )
            FlipCardHalf(
                text,
                top = false,
                style,
                colors.cardBottom,
                corner,
                modifier = Modifier.flap { ClockMath.flipBottomAngle(progress.value) },
                shade = { ClockMath.flipShade(progress.value) },
            )
        }
        FlipCardHinge(colors.background, Modifier.matchParentSize())
    }
}

/**
 * 卡片的上半張或下半張：整張卡片一樣大，畫的時候只留一半。
 * 這樣中心點剛好是接縫，翻動時直接繞著中心轉就好。
 */
@Composable
private fun FlipCardHalf(
    text: String,
    top: Boolean,
    style: TextStyle,
    color: Color,
    corner: Dp,
    modifier: Modifier = Modifier,
    shade: () -> Float = { 0f },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val middle = size.height / 2f
                clipRect(top = if (top) 0f else middle, bottom = if (top) middle else size.height) {
                    this@drawWithContent.drawContent()
                    val alpha = shade()
                    if (alpha > 0f) {
                        drawRoundRect(Color.Black.copy(alpha = alpha), cornerRadius = CornerRadius(corner.toPx()))
                    }
                }
            }
            .background(color, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style, softWrap = false, maxLines = 1)
    }
}

/** 翻動中的那片：繞著卡片中心的水平軸轉，翻到側面（±90°）時藏起來。 */
private fun Modifier.flap(angle: () -> Float): Modifier = graphicsLayer {
    val degrees = angle()
    rotationX = degrees
    alpha = if (abs(degrees) >= 90f) 0f else 1f
    // 鏡頭拉遠一點，高解析度螢幕上翻轉時才不會變形得太誇張
    cameraDistance = 12f * density
}

/** 卡片中間的接縫，左右兩側各有一個小凹口，像真的翻頁鐘。 */
@Composable
private fun FlipCardHinge(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val middle = size.height / 2f
        val seam = (size.width * 0.012f).coerceAtLeast(1.dp.toPx())
        drawRect(color, topLeft = Offset(0f, middle - seam / 2f), size = Size(size.width, seam))
        val notch = size.width * 0.025f
        drawCircle(color, notch, Offset(0f, middle))
        drawCircle(color, notch, Offset(size.width, middle))
    }
}

/** 時、分的卡片翻一次的時間。 */
private const val FLIP_MILLIS = 600

/** 秒數卡的翻頁時間。 */
private const val SECONDS_FLIP_MILLIS = 250

/** 秒針每跳一格的動畫時間。 */
private const val SECOND_TICK_MILLIS = 150

/** 翻頁卡片用系統內建的窄體粗體字（Roboto Condensed），兩位數字可以放得比較大，也不用另外打包字型。 */
private val FlipFontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
