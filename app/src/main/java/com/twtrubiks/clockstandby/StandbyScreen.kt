package com.twtrubiks.clockstandby

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.service.dreams.DreamService
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** 防烙印時，每分鐘整個畫面最多移動的距離。 */
private val BurnInDistance = 8.dp

private val LowBatteryColor = Color(0xFFFF453A)

/** 控制列（錶面指示點和設定按鈕）的高度。 */
private val ControlsBarHeight = 56.dp

/** 控制列顯示時，時鐘要往上讓出的高度：控制列加上底部間距。 */
private val ControlsReserve = ControlsBarHeight + 8.dp

/** 控制列沒有操作多久之後自動收起。 */
private const val CONTROLS_AUTO_HIDE_MS = 5000L

/** 夜間模式時這個畫面的螢幕亮度（0～1），離開時還原成系統亮度。 */
private const val NIGHT_SCREEN_BRIGHTNESS = 0.05f

/**
 * 待機時鐘。預設只顯示時鐘、日期和電量；點一下時鐘才叫出控制列，沒有操作 5 秒後自動收起。
 * 錶面用左右滑動切換，其他設定收在設定面板裡。
 */
@Composable
fun StandbyScreen(
    onShowIntro: () -> Unit,
    onSettingsChange: (ClockSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalClockSettings.current
    StandbyWindowEffect(night = settings.nightMode)

    val faces = ClockFace.entries
    val pagerState = rememberPagerState(initialPage = settings.face.ordinal) { faces.size }
    val scope = rememberCoroutineScope()
    var controlsVisible by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    // 每次操作控制列就加一，自動收起的倒數重新開始
    var interactions by remember { mutableIntStateOf(0) }
    val colors = animateClockColors(settings.nightMode)

    // 兩指縮放時先改這個值，手指放開才交給 MainActivity 保存。
    // 不能用 settings 當 key：保存後換成新的 state，pinchToZoom 裡抓的還是舊的，之後就縮放不了
    var liveScale by remember { mutableFloatStateOf(settings.clockScale) }
    var scaleHintVisible by remember { mutableStateOf(false) }
    var scaleHintKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(scaleHintKey) {
        if (scaleHintKey > 0) {
            delay(1500)
            scaleHintVisible = false
        }
    }
    // pinchToZoom 和換頁的 collect 只在第一次組合時建立，要透過這兩個 state 拿到最新的設定與 callback
    val currentSettings by rememberUpdatedState(settings)
    val currentOnSettingsChange by rememberUpdatedState(onSettingsChange)

    // 設定面板打開時不收，關掉之後重新倒數
    LaunchedEffect(controlsVisible, settingsOpen, interactions) {
        if (controlsVisible && !settingsOpen) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }
    // 換錶面時記住選擇（螢幕保護程式也顯示這個），並短暫顯示指示點，讓使用者知道現在是第幾個
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            if (faces[page] != currentSettings.face) {
                currentOnSettingsChange(currentSettings.copy(face = faces[page]))
            }
            controlsVisible = true
            interactions++
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val landscape = maxWidth > maxHeight
        // 控制列顯示時時鐘往上讓出位置，收起來時再放大回去
        val controlsReserve by animateDpAsState(
            targetValue = if (controlsVisible) ControlsReserve else 0.dp,
            label = "controlsReserve",
        )

        StandbyFrame(colors = colors, landscape = landscape, dimStatus = settings.nightMode) {
            CompositionLocalProvider(LocalClockSettings provides settings.copy(clockScale = liveScale)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = controlsReserve)
                        .pinchToZoom(
                            onZoom = { zoom ->
                                liveScale = ClockMath.clampClockScale(liveScale * zoom)
                                scaleHintVisible = true
                            },
                            onEnd = {
                                currentOnSettingsChange(currentSettings.copy(clockScale = liveScale))
                                scaleHintKey++
                            },
                        ),
                ) { page ->
                    ClockFaceContent(
                        face = faces[page],
                        colors = colors,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                onClickLabel = if (controlsVisible) "隱藏控制列" else "顯示控制列",
                            ) {
                                controlsVisible = !controlsVisible
                                interactions++
                            }
                            .padding(
                                horizontal = 24.dp,
                                vertical = if (landscape) 8.dp else 24.dp,
                            ),
                    )
                }
            }
        }

        // 縮放時在上方顯示目前大小，放開 1.5 秒後淡出
        AnimatedVisibility(
            visible = scaleHintVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 12.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = "時鐘大小 ${ClockMath.clockScalePercent(liveScale)}%",
                color = colors.primary,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !settingsOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 8.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ControlsBar(
                faces = faces,
                selectedPage = pagerState.currentPage,
                colors = colors,
                onSelectFace = { face ->
                    interactions++
                    scope.launch { pagerState.animateScrollToPage(face.ordinal) }
                },
                onOpenSettings = { settingsOpen = true },
            )
        }

        SettingsPanel(
            visible = settingsOpen,
            settings = settings,
            colors = colors,
            onDismiss = {
                settingsOpen = false
                interactions++
            },
            onSettingsChange = onSettingsChange,
            onShowIntro = {
                settingsOpen = false
                onShowIntro()
            },
        )
    }
}

/**
 * 待機畫面和螢幕保護程式共用的版面：上方是日期與電量，下面放錶面。
 * 整塊每分鐘往不同方向微微移動，避免螢幕烙印。
 */
@Composable
internal fun StandbyFrame(
    colors: ClockColors,
    landscape: Boolean,
    dimStatus: Boolean,
    modifier: Modifier = Modifier,
    clock: @Composable ColumnScope.() -> Unit,
) {
    // 只在換分鐘 / 換日時才重組，秒數的更新留給各個錶面自己讀
    val now = rememberTimeEverySecond()
    val minuteOfDay by remember { derivedStateOf { now.value.hour * 60 + now.value.minute } }
    val date by remember { derivedStateOf { now.value.toLocalDate() } }
    val battery by rememberBatteryStatus()

    val shift = ClockMath.burnInShift(minuteOfDay)
    val offsetX by animateDpAsState(BurnInDistance * shift.x, tween(1500), label = "burnInX")
    val offsetY by animateDpAsState(BurnInDistance * shift.y, tween(1500), label = "burnInY")

    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .offset { IntOffset(offsetX.roundToPx(), offsetY.roundToPx()) },
    ) {
        StatusRow(
            // 橫屏放在桌上時用 2026/10/28 這種數字日期
            date = if (landscape) ClockMath.formatNumericDate(date) else ClockMath.formatDate(date),
            battery = battery,
            colors = colors,
            // 橫屏高度寶貴，狀態列和錶面的留白都縮小，讓時鐘放大；夜間模式再調暗，畫面上最亮的只有時鐘
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = if (landscape) 10.dp else 20.dp)
                .alpha(if (dimStatus) 0.6f else 1f),
        )
        clock()
    }
}

/** 控制列：中間是錶面的指示點（也可以點），右邊是設定按鈕。只畫外框，待機時不會太亮。 */
@Composable
private fun ControlsBar(
    faces: List<ClockFace>,
    selectedPage: Int,
    colors: ClockColors,
    onSelectFace: (ClockFace) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ControlsBarHeight)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左右各佔一半剩餘空間，指示點才會在正中間
        Spacer(Modifier.weight(1f))
        FaceIndicator(faces, selectedPage, colors, onSelectFace)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            SettingsButton(colors, onOpenSettings)
        }
    }
}

@Composable
private fun FaceIndicator(
    faces: List<ClockFace>,
    selectedPage: Int,
    colors: ClockColors,
    onSelect: (ClockFace) -> Unit,
) {
    Row(Modifier.selectableGroup()) {
        faces.forEach { face ->
            val selected = face.ordinal == selectedPage
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) colors.primary else colors.secondary,
                label = "dotColor",
            )
            // 點本身很小，外面包一層夠大的點擊範圍；錶面名稱放在無障礙描述，測試也用它來找
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 44.dp)
                    .semantics { contentDescription = face.label }
                    .selectable(selected = selected, role = Role.Tab) { onSelect(face) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = width, height = 8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(colors: ClockColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, colors.secondary, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "設定" },
        contentAlignment = Alignment.Center,
    ) {
        TuneIcon(colors.primary, Modifier.size(20.dp))
    }
}

/** 三條橫線加上滑桿的設定圖示；沒有引入 material-icons，自己畫。 */
@Composable
private fun TuneIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.8.dp.toPx()
        val knob = 2.8.dp.toPx()
        // 每條線的高度位置，和滑桿圓點所在的水平位置
        listOf(0.2f to 0.7f, 0.5f to 0.3f, 0.8f to 0.6f).forEach { (y, x) ->
            val cy = size.height * y
            drawLine(
                color = color,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(color, knob, Offset(size.width * x, cy))
        }
    }
}

/** 設定面板：從下方滑出，點旁邊的空白處或按返回鍵關閉。 */
@Composable
private fun SettingsPanel(
    visible: Boolean,
    settings: ClockSettings,
    colors: ClockColors,
    onDismiss: () -> Unit,
    onSettingsChange: (ClockSettings) -> Unit,
    onShowIntro: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = "關閉設定",
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(colors.surface)
                    // 吃掉面板上的點擊，才不會穿過去點到後面的遮罩，把面板關掉
                    .pointerInput(Unit) { detectTapGestures() }
                    .safeDrawingPadding()
                    // 橫屏高度不夠時可以捲動
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
            ) {
                Text(
                    text = "設定",
                    color = colors.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                SettingSwitchRow("夜間模式", settings.nightMode, colors) {
                    onSettingsChange(settings.copy(nightMode = it))
                }
                SettingSwitchRow("24 小時制", settings.use24Hour, colors) {
                    onSettingsChange(settings.copy(use24Hour = it))
                }
                SettingSwitchRow("顯示秒數", settings.showSeconds, colors) {
                    onSettingsChange(settings.copy(showSeconds = it))
                }
                SettingActionRow(
                    label = "充電時自動顯示",
                    description = "在系統的螢幕保護程式選擇「時鐘待機」，並設定充電時啟動",
                    colors = colors,
                ) { context.openDreamSettings() }
                SettingActionRow(label = "介紹", colors = colors, onClick = onShowIntro)
            }
        }
    }
}

/** 整列都可以點；Switch 本身不處理點擊，無障礙服務和測試看到的是一個有文字的開關。 */
@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    colors: ClockColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.primary, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.background,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.secondary,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.secondary,
            ),
        )
    }
}

@Composable
private fun SettingActionRow(
    label: String,
    colors: ClockColors,
    description: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = colors.primary, fontSize = 17.sp)
        description?.let {
            Text(it, color = colors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** 打開系統的螢幕保護程式設定；少數裝置沒有這一頁，就改開顯示設定。 */
private fun Context.openDreamSettings() {
    try {
        startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }
}

@Composable
private fun StatusRow(date: String, battery: BatteryStatus?, colors: ClockColors, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date,
            style = TextStyle(color = colors.secondary, fontSize = 18.sp, fontFeatureSettings = "tnum"),
        )
        battery?.let { BatteryIndicator(it, colors) }
    }
}

/** 右上角的電量：充電時有閃電、圖示依電量填滿，低電量變紅色。 */
@Composable
private fun BatteryIndicator(battery: BatteryStatus, colors: ClockColors) {
    val low = ClockMath.isLowBattery(battery.percent, battery.charging)
    val fill = when {
        battery.charging -> colors.accent
        low -> LowBatteryColor
        else -> colors.primary
    }
    val description = if (battery.charging) "電量 ${battery.percent}%，充電中" else "電量 ${battery.percent}%"
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (battery.charging) {
            ChargingBolt(colors.accent, Modifier.size(width = 10.dp, height = 16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "${battery.percent}%",
            style = TextStyle(
                color = if (low) LowBatteryColor else colors.secondary,
                fontSize = 18.sp,
                fontFeatureSettings = "tnum",
            ),
        )
        Spacer(Modifier.width(8.dp))
        BatteryIcon(
            percent = battery.percent,
            outline = colors.secondary,
            fill = fill,
            modifier = Modifier.size(width = 32.dp, height = 16.dp),
        )
    }
}

@Composable
private fun BatteryIcon(percent: Int, outline: Color, fill: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        val nubWidth = 2.5.dp.toPx()
        val bodyWidth = size.width - nubWidth - 1.dp.toPx()
        drawRoundRect(
            color = outline,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = outline,
            topLeft = Offset(size.width - nubWidth, size.height * 0.3f),
            size = Size(nubWidth, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val inset = stroke + 1.5.dp.toPx()
        drawRoundRect(
            color = fill,
            topLeft = Offset(inset, inset),
            size = Size((bodyWidth - inset * 2) * percent / 100f, size.height - inset * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun ChargingBolt(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.65f, 0f)
            lineTo(0f, h * 0.58f)
            lineTo(w * 0.45f, h * 0.58f)
            lineTo(w * 0.35f, h)
            lineTo(w, h * 0.42f)
            lineTo(w * 0.55f, h * 0.42f)
            close()
        }
        drawPath(bolt, color)
    }
}

/**
 * 兩指縮放。只處理兩指以上的手勢，而且在 Initial pass 先攔下來，
 * 單指的左右滑動（換錶面）和點擊（顯示控制列）照常交給 pager 和錶面。
 */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit, onEnd: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zooming = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                // 一旦開始縮放，整個手勢都攔下來，避免剩一指時被當成滑動或點擊
                if (zooming || event.changes.count { it.pressed } >= 2) {
                    zooming = true
                    val zoom = event.calculateZoom()
                    if (zoom != 1f) onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (zooming) onEnd()
        }
    }

/**
 * 待機時保持螢幕常亮並隱藏系統列；夜間模式把這個視窗的亮度調到最低。離開時都還原。
 * App 和螢幕保護程式共用，視窗分別來自 Activity 和 DreamService。
 */
@Composable
internal fun StandbyWindowEffect(night: Boolean) {
    val view = LocalView.current
    val window = remember(view) { view.context.findWindow() }
    DisposableEffect(view, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    // 只改這個視窗的亮度，不動系統設定，也不用光感測器
    DisposableEffect(window, night) {
        window?.setScreenBrightness(
            if (night) NIGHT_SCREEN_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
        )
        onDispose { window?.setScreenBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
}

private fun Window.setScreenBrightness(brightness: Float) {
    attributes = attributes.apply { screenBrightness = brightness }
}

private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is DreamService -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}
