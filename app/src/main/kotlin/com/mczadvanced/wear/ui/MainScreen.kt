package com.mczadvanced.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import com.mczadvanced.wear.StoveApp
import com.mczadvanced.wear.data.LinkStatus
import com.mczadvanced.wear.data.StoveUiState
import com.mczadvanced.wear.formatAge
import com.mczadvanced.wear.formatTemp
import com.mczadvanced.wear.protocol.Registers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    app: StoveApp,
    onPower: () -> Unit,
    onMode: () -> Unit,
    onPowerLevel: () -> Unit,
    onFan: () -> Unit,
    onChangeStove: () -> Unit,
) {
    val ui by app.repository.state.collectAsStateWithLifecycle()
    val stove = ui.stove
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        app.repository.loadCache()
        app.repository.refresh()
    }

    // Setpoint taps are batched: the write goes out 800 ms after the last tap.
    var target by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        delay(800)
        app.repository.setSetpoint(t)
        target = null
    }
    val shownSetpoint = target ?: stove.setpoint
    fun step(delta: Float) {
        val base = shownSetpoint ?: return
        target = (base + delta).coerceIn(Registers.SETPOINT_MIN, Registers.SETPOINT_MAX)
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(stove.stateLabel) } }
            item { Text(formatTemp(stove.roomTemp), style = MaterialTheme.typography.displayMedium) }
            item { Text(statusLine(ui), style = MaterialTheme.typography.labelSmall) }
            if (stove.hasAlarm) {
                item {
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        label = { Text("Alarm ${stove.alarm}") },
                    )
                }
            }
            item {
                Button(
                    onClick = onPower,
                    enabled = stove.isOn != null,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (stove.isOn == true) "Turn off" else "Turn on") },
                )
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = { step(-0.5f) }, enabled = shownSetpoint != null) { Text("−") }
                    Text("Set ${formatTemp(shownSetpoint)}")
                    TextButton(onClick = { step(0.5f) }, enabled = shownSetpoint != null) { Text("+") }
                }
            }
            item {
                FilledTonalButton(
                    onClick = onMode,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mode") },
                    secondaryLabel = { Text(stove.modeLabel) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onPowerLevel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Power") },
                    secondaryLabel = { Text(stove.powerLevel?.toString() ?: "--") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onFan,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Fan") },
                    secondaryLabel = { Text(stove.fanLabel) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = { app.appScope.launch { app.repository.refresh() } },
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Refresh") },
                )
            }
            item {
                OutlinedButton(
                    onClick = onChangeStove,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Change stove") },
                )
            }
        }
    }
}

private fun statusLine(ui: StoveUiState): String = when {
    ui.busy && ui.link == LinkStatus.CONNECTING -> "Connecting…"
    ui.busy -> "Updating…"
    ui.link == LinkStatus.UNREACHABLE -> "${ui.error ?: "Unreachable"} · ${formatAge(ui.updatedAt, System.currentTimeMillis())}"
    else -> "Updated ${formatAge(ui.updatedAt, System.currentTimeMillis())}"
}
