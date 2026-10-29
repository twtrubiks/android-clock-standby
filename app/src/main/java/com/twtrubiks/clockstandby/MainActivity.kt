package com.twtrubiks.clockstandby

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 背景一律是黑色，系統列圖示固定用淺色
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val introSeen = prefs.getBoolean(KEY_INTRO_SEEN, false)
        val settingsStore = ClockSettingsStore(prefs, systemUse24Hour = DateFormat.is24HourFormat(this))
        val initialSettings = settingsStore.load()
        setContent {
            var settings by remember { mutableStateOf(initialSettings) }
            ClockStandbyApp(
                introSeen = introSeen,
                settings = settings,
                onIntroFinished = { prefs.edit { putBoolean(KEY_INTRO_SEEN, true) } },
                onSettingsChange = {
                    settings = it
                    settingsStore.save(it)
                },
            )
        }
    }

    companion object {
        const val PREFS_NAME = "clock_standby"
        const val KEY_INTRO_SEEN = "intro_seen"
    }
}
