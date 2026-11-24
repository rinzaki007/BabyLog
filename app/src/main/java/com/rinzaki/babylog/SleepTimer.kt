package com.rinzaki.babylog

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal data class SleepTimerState(
    val startedAtEpochMs: Long = 0L,
    val runningSinceEpochMs: Long = 0L,
    val elapsedBeforePauseMs: Long = 0L
) {
    val isActive: Boolean get() = startedAtEpochMs > 0L
    val isRunning: Boolean get() = isActive && runningSinceEpochMs > 0L
}

internal data class CompletedSleep(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMinutes: Int
)

internal fun sleepTimerElapsedMs(state: SleepTimerState, now: Long): Long {
    if (!state.isActive) return 0L
    val currentRun = if (state.isRunning) (now - state.runningSinceEpochMs).coerceAtLeast(0L) else 0L
    return state.elapsedBeforePauseMs + currentRun
}

internal fun startSleepTimer(now: Long): SleepTimerState =
    SleepTimerState(startedAtEpochMs = now, runningSinceEpochMs = now)

internal fun pauseSleepTimer(state: SleepTimerState, now: Long): SleepTimerState {
    if (!state.isRunning) return state
    return state.copy(
        runningSinceEpochMs = 0L,
        elapsedBeforePauseMs = sleepTimerElapsedMs(state, now)
    )
}

internal fun resumeSleepTimer(state: SleepTimerState, now: Long): SleepTimerState {
    if (!state.isActive || state.isRunning) return state
    return state.copy(runningSinceEpochMs = now)
}

internal fun completeSleepTimer(state: SleepTimerState, now: Long): CompletedSleep? {
    if (!state.isActive) return null
    val elapsed = sleepTimerElapsedMs(state, now)
    return CompletedSleep(
        startedAtEpochMs = state.startedAtEpochMs,
        endedAtEpochMs = now,
        durationMinutes = ((elapsed + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    )
}

internal object SleepTimerStore {
    private const val FILE = "babylog_sleep_timer"

    @Synchronized
    fun load(context: Context): SleepTimerState {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val state = SleepTimerState(
            startedAtEpochMs = prefs.getLong("started_at", 0L),
            runningSinceEpochMs = prefs.getLong("running_since", 0L),
            elapsedBeforePauseMs = prefs.getLong("elapsed_before_pause", 0L).coerceAtLeast(0L)
        )
        return if (state.startedAtEpochMs <= 0L ||
            (state.isRunning && state.runningSinceEpochMs < state.startedAtEpochMs)
        ) SleepTimerState() else state
    }

    @Synchronized
    fun save(context: Context, state: SleepTimerState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("started_at", state.startedAtEpochMs)
            .putLong("running_since", state.runningSinceEpochMs)
            .putLong("elapsed_before_pause", state.elapsedBeforePauseMs)
            .apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

@Composable
internal fun SleepTimerCard(onComplete: (Long, Long, Int) -> Unit) {
    val context = LocalContext.current.applicationContext
    var state by remember { mutableStateOf(SleepTimerStore.load(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.runningSinceEpochMs) {
        while (state.isRunning) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val elapsed = sleepTimerElapsedMs(state, now)
    val hours = elapsed / 3_600_000L
    val minutes = (elapsed / 60_000L) % 60L
    val seconds = (elapsed / 1_000L) % 60L
    val elapsedLabel = "%02d:%02d:%02d".format(hours, minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("睡眠计时", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !state.isActive -> "开始记录宝宝入睡时间，结束后自动生成睡眠记录"
                            state.isRunning -> "正在记录 · 离开页面或关闭 App 也会继续"
                            else -> "已暂停 · 可以继续或结束本次睡眠"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (state.isActive) {
                    Text(
                        elapsedLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (!state.isActive) {
                Button(
                    onClick = {
                        state = startSleepTimer(System.currentTimeMillis())
                        now = System.currentTimeMillis()
                        SleepTimerStore.save(context, state)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始睡眠计时") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isRunning) {
                        FilledTonalButton(
                            onClick = {
                                state = pauseSleepTimer(state, System.currentTimeMillis())
                                now = System.currentTimeMillis()
                                SleepTimerStore.save(context, state)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("暂停") }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                state = resumeSleepTimer(state, System.currentTimeMillis())
                                now = System.currentTimeMillis()
                                SleepTimerStore.save(context, state)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("继续") }
                    }
                    OutlinedButton(
                        onClick = {
                            val endedAt = System.currentTimeMillis()
                            val completed = completeSleepTimer(state, endedAt)
                            if (completed != null) {
                                onComplete(
                                    completed.startedAtEpochMs,
                                    completed.endedAtEpochMs,
                                    completed.durationMinutes
                                )
                            }
                            SleepTimerStore.clear(context)
                            state = SleepTimerState()
                            now = endedAt
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("结束并保存") }
                }
            }
        }
    }
}
