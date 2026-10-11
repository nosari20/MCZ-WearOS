package com.mczadvanced.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.mczadvanced.wear.StoveApp
import com.mczadvanced.wear.data.LinkStatus
import kotlinx.coroutines.launch

/** Explicit confirmation before toggling, also the target of the tile's power button. */
@Composable
fun ConfirmPowerScreen(app: StoveApp, onDone: () -> Unit) {
    val ui by app.repository.state.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    val isOn = ui.stove.isOn

    LaunchedEffect(Unit) {
        app.repository.loadCache()
        app.repository.refresh()
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader {
                    Text(
                        when {
                            isOn == null && ui.link == LinkStatus.UNREACHABLE -> ui.error ?: "Unreachable"
                            isOn == null -> "Connecting…"
                            isOn -> "Turn stove off?"
                            else -> "Turn stove on?"
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Button(
                    onClick = {
                        val target = isOn == false
                        app.appScope.launch { app.repository.setOn(target) }
                        onDone()
                    },
                    enabled = isOn != null && !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isOn == true) "Turn off" else "Turn on") },
                )
            }
            item {
                FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth(), label = { Text("Cancel") })
            }
        }
    }
}
