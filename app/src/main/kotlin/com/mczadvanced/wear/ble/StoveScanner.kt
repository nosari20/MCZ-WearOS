package com.mczadvanced.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.mczadvanced.wear.data.StoveException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class FoundStove(val address: String, val name: String, val rssi: Int)

/** Scans for Maestro modules (advertised name "MCZ_EP..."). Caller must hold BLUETOOTH_SCAN. */
@SuppressLint("MissingPermission")
class StoveScanner(private val context: Context) {
    fun scan(): Flow<FoundStove> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            close(StoveException("Bluetooth is off"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: return
                if (name.startsWith(NAME_PREFIX)) trySend(FoundStove(result.device.address, name, result.rssi))
            }

            override fun onScanFailed(errorCode: Int) {
                close(StoveException("Scan failed ($errorCode)"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            close(StoveException("Bluetooth permission missing"))
            return@callbackFlow
        }
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    companion object {
        const val NAME_PREFIX = "MCZ_EP"
    }
}
