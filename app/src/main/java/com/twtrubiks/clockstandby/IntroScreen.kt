package com.twtrubiks.clockstandby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class IntroPage(val title: String, val body: String) {
    Standby("時鐘待機", "充電時把手機橫放在桌上，閒置的螢幕就變成一個大字的床邊時鐘；也能設成螢幕保護程式，充電時自動出現。"),
    Faces("三種錶面", "數位、指針、翻頁三種風格，左右滑動就能切換；點一下時鐘叫出設定，兩指縮放可以調整時鐘大小。"),
    Night("夜間模式", "低亮度的紅色不刺眼也不打擾睡眠，螢幕亮度也會調到最低；畫面每分鐘還會微微移動，避免螢幕烙印。"),
}

@Composable
fun IntroScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val pages = IntroPage.entries
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onFinish) { Text("略過", fontSize = 16.sp) }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            IntroPageContent(pages[page])
        }
        PageIndicator(count = pages.size, current = pagerState.currentPage)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (isLastPage) {
                    onFinish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            AnimatedContent(
                targetState = if (isLastPage) "開始使用" else "下一步",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "introButton",
            ) { Text(it, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun IntroPageContent(page: IntroPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PhoneFrame {
            when (page) {
                IntroPage.Standby -> DigitalClockFace(DayClockColors, Modifier.fillMaxSize())
                IntroPage.Faces -> CyclingFaces()
                IntroPage.Night -> AnalogClockFace(NightClockColors, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = page.title,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = page.body,
            color = DayClockColors.secondary,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 橫放在桌上的手機外框，裡面放即時運作的錶面預覽。 */
@Composable
private fun PhoneFrame(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2.1f)
            .clip(shape)
            .background(Color.Black)
            .border(4.dp, Color(0xFF3A3A3C), shape)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 預覽固定用預設大小，不受使用者在待機畫面的縮放影響
        val preview = LocalClockSettings.current.copy(clockScale = ClockMath.DEFAULT_CLOCK_SCALE)
        CompositionLocalProvider(LocalClockSettings provides preview) {
            content()
        }
    }
}

/** 介紹頁的預覽：每 2 秒輪流換一種錶面。 */
@Composable
private fun CyclingFaces() {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            index = (index + 1) % ClockFace.entries.size
        }
    }
    Crossfade(targetState = ClockFace.entries[index], animationSpec = tween(500), label = "cyclingFaces") {
        ClockFaceContent(it, DayClockColors, Modifier.fillMaxSize())
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3C),
                label = "dotColor",
            )
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
