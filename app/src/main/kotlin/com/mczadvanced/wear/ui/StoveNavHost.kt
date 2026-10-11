package com.mczadvanced.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.mczadvanced.wear.StoveApp
import com.mczadvanced.wear.protocol.Registers
import com.mczadvanced.wear.protocol.StoveMode
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val PAIRING = "pairing"
private const val MAIN = "main"
private const val MODE = "mode"
private const val POWER = "power"
private const val FAN = "fan"
private const val CONFIRM_POWER = "confirm_power"

private data class Loaded(val address: String?)

@Composable
fun StoveNavHost(
    app: StoveApp,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    confirmPower: Boolean,
    onConfirmPowerHandled: () -> Unit,
) {
    val loaded by remember { app.settings.stove.map { Loaded(it?.address) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val ui by app.repository.state.collectAsStateWithLifecycle()
    val repo = app.repository

    MaterialTheme {
        AppScaffold {
            val current = loaded ?: return@AppScaffold
            val nav = rememberSwipeDismissableNavController()
            val start = if (current.address == null) PAIRING else MAIN

            LaunchedEffect(confirmPower, current.address) {
                if (confirmPower && current.address != null) {
                    nav.navigate(CONFIRM_POWER)
                    onConfirmPowerHandled()
                }
            }

            SwipeDismissableNavHost(navController = nav, startDestination = start) {
                composable(PAIRING) {
                    PairingScreen(
                        app = app,
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = onRequestPermissions,
                        onPaired = { nav.navigate(MAIN) { popUpTo(PAIRING) { inclusive = true } } },
                    )
                }
                composable(MAIN) {
                    MainScreen(
                        app = app,
                        onPower = { nav.navigate(CONFIRM_POWER) },
                        onMode = { nav.navigate(MODE) },
                        onPowerLevel = { nav.navigate(POWER) },
                        onFan = { nav.navigate(FAN) },
                        onChangeStove = { nav.navigate(PAIRING) { popUpTo(MAIN) { inclusive = true } } },
                    )
                }
                composable(MODE) {
                    PickerScreen(
                        title = "Mode",
                        options = StoveMode.entries.map { it.label to it.code },
                        selected = ui.stove.mode,
                        onPick = { code ->
                            StoveMode.of(code)?.let { app.appScope.launch { repo.setMode(it) } }
                            nav.popBackStack()
                        },
                    )
                }
                composable(POWER) {
                    PickerScreen(
                        title = "Power",
                        options = (1..5).map { "Level $it" to it },
                        selected = ui.stove.powerLevel,
                        onPick = { level ->
                            app.appScope.launch { repo.setPowerLevel(level) }
                            nav.popBackStack()
                        },
                    )
                }
                composable(FAN) {
                    PickerScreen(
                        title = "Fan",
                        options = listOf("Auto" to Registers.FAN_AUTO) + (1..5).map { "Level $it" to it },
                        selected = ui.stove.fan,
                        onPick = { level ->
                            app.appScope.launch { repo.setFan(level) }
                            nav.popBackStack()
                        },
                    )
                }
                composable(CONFIRM_POWER) {
                    ConfirmPowerScreen(app = app, onDone = { nav.popBackStack() })
                }
            }
        }
    }
}
