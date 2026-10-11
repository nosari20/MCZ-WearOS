package com.mczadvanced.wear.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.mczadvanced.wear.StoveApp

class MainActivity : ComponentActivity() {
    private val permissionsGranted = mutableStateOf(false)
    private val confirmPower = mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsGranted.value = hasPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsGranted.value = hasPermissions()
        confirmPower.value = intent.getBooleanExtra(EXTRA_CONFIRM_POWER, false)
        setContent {
            StoveNavHost(
                app = StoveApp.get(this),
                permissionsGranted = permissionsGranted.value,
                onRequestPermissions = ::requestPermissions,
                confirmPower = confirmPower.value,
                onConfirmPowerHandled = { confirmPower.value = false },
            )
        }
        if (!permissionsGranted.value) requestPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_CONFIRM_POWER, false)) confirmPower.value = true
    }

    override fun onResume() {
        super.onResume()
        permissionsGranted.value = hasPermissions()
    }

    private fun hasPermissions() = PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun requestPermissions() = permissionLauncher.launch(PERMISSIONS)

    companion object {
        const val EXTRA_CONFIRM_POWER = "confirm_power"
        private val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    }
}
