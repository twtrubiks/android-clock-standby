package com.twtrubiks.clockstandby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime

data class BatteryStatus(val percent: Int, val charging: Boolean)

/** 每到整秒更新一次的目前時間（毫秒歸零）。 */
@Composable
fun rememberTimeEverySecond(): State<LocalDateTime> =
    produceState(LocalDateTime.now().withNano(0)) {
        while (true) {
            val now = LocalDateTime.now()
            value = now.withNano(0)
            delay(1000L - now.nano / 1_000_000)
        }
    }

/** 監聽電量廣播；ACTION_BATTERY_CHANGED 是 sticky broadcast，註冊後馬上就有值。 */
@Composable
fun rememberBatteryStatus(): State<BatteryStatus?> {
    val context = LocalContext.current
    return produceState<BatteryStatus?>(null, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = intent.toBatteryStatus()
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { value = it.toBatteryStatus() }
        awaitDispose { context.unregisterReceiver(receiver) }
    }
}

private fun Intent.toBatteryStatus(): BatteryStatus? {
    val percent = ClockMath.batteryPercent(
        level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
    ) ?: return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus(percent, charging)
}
