package com.twtrubiks.clockstandby

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private enum class Screen { Intro, Standby }

/** 第一次開啟先看介紹，之後直接進入待機時鐘；待機畫面可以再點「介紹」回來看。 */
@Composable
fun ClockStandbyApp(
    introSeen: Boolean,
    settings: ClockSettings,
    onIntroFinished: () -> Unit,
    onSettingsChange: (ClockSettings) -> Unit,
) {
    var seen by rememberSaveable { mutableStateOf(introSeen) }
    var screen by rememberSaveable { mutableStateOf(if (introSeen) Screen.Standby else Screen.Intro) }

    BackHandler(enabled = screen == Screen.Intro && seen) { screen = Screen.Standby }

    CompositionLocalProvider(LocalClockSettings provides settings) {
        ClockStandbyTheme {
            Crossfade(targetState = screen, animationSpec = tween(600), label = "screen") { target ->
                when (target) {
                    Screen.Intro -> IntroScreen(
                        onFinish = {
                            if (!seen) {
                                seen = true
                                onIntroFinished()
                            }
                            screen = Screen.Standby
                        },
                    )
                    Screen.Standby -> StandbyScreen(
                        onShowIntro = { screen = Screen.Intro },
                        onSettingsChange = onSettingsChange,
                    )
                }
            }
        }
    }
}
