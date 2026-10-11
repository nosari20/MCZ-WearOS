package com.mczadvanced.wear.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.mczadvanced.wear.BuildConfig
import com.mczadvanced.wear.StoveApp
import com.mczadvanced.wear.ble.FoundStove
import com.mczadvanced.wear.ble.StoveBonding
import com.mczadvanced.wear.ble.StoveScanner
import com.mczadvanced.wear.data.DemoStoveTransport
import com.mczadvanced.wear.data.SavedStove
import com.mczadvanced.wear.data.StoveException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PairingScreen(
    app: StoveApp,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onPaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState()
    val found = remember { mutableStateMapOf<String, FoundStove>() }
    var round by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(round, permissionsGranted) {
        if (!permissionsGranted) return@LaunchedEffect
        found.clear()
        error = null
        scanning = true
        withTimeoutOrNull(SCAN_MS) {
            StoveScanner(context).scan()
                .catch { error = it.message }
                .collect { found[it.address] = it }
        }
        scanning = false
    }

    fun pick(stove: SavedStove) = scope.launch {
        if (pairing != null) return@launch
        pairing = stove.name
        error = null
        try {
            if (stove.address != DemoStoveTransport.ADDRESS) StoveBonding(context).bond(stove.address)
            app.repository.reset()
            app.settings.save(stove)
            onPaired()
        } catch (e: StoveException) {
            error = e.message
        } finally {
            pairing = null
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Pair stove") } }
            if (!permissionsGranted) {
                item { Text("Bluetooth permission needed", textAlign = TextAlign.Center) }
                item {
                    Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth(), label = { Text("Grant") })
                }
            } else {
                item {
                    Text(
                        "On the stove panel press + and − together until the Bluetooth icon blinks.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                items(found.values.sortedByDescending { it.rssi }) { stove ->
                    Button(
                        onClick = { pick(SavedStove(stove.address, stove.name)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stove.name) },
                        secondaryLabel = { Text(stove.address) },
                    )
                }
                item {
                    Text(
                        when {
                            pairing != null -> "Pairing with $pairing… accept the prompt if one appears"
                            error != null -> error!!
                            scanning -> "Scanning…"
                            found.isEmpty() -> "No stove found"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!scanning) {
                    item {
                        FilledTonalButton(
                            onClick = { round++ },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Scan again") },
                        )
                    }
                }
            }
            if (BuildConfig.DEBUG) {
                item {
                    FilledTonalButton(
                        onClick = { pick(SavedStove(DemoStoveTransport.ADDRESS, "Demo stove")) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Demo stove") },
                    )
                }
            }
        }
    }
}

private const val SCAN_MS = 15_000L
