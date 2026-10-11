package com.mczadvanced.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.mczadvanced.wear.data.StoveException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Just works" bonding with the Maestro module. The stove refuses links from unbonded devices,
 * and accepts a new bond only while its panel is in pairing mode (+ and - pressed together).
 * Called from the pairing screen only, never in the background.
 */
@SuppressLint("MissingPermission")
class StoveBonding(private val context: Context) {

    /** Returns once [address] is bonded; throws [StoveException] with a user-facing reason otherwise. */
    suspend fun bond(address: String) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw StoveException("Bluetooth unavailable")
        if (!adapter.isEnabled) throw StoveException("Bluetooth is off")
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
            ?: throw StoveException("Invalid stove address")
        if (device.bondState == BluetoothDevice.BOND_BONDED) return

        val result = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (d?.address != device.address) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                if (state != BluetoothDevice.BOND_BONDING) result.complete(state)
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_EXPORTED)
        try {
            if (!device.createBond()) throw StoveException(PAIRING_FAILED)
            val state = withTimeoutOrNull(BOND_TIMEOUT_MS) { result.await() }
            Log.i(TAG, "Bond result $state")
            if (state != BluetoothDevice.BOND_BONDED) throw StoveException(PAIRING_FAILED)
        } catch (e: SecurityException) {
            throw StoveException("Bluetooth permission missing")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private companion object {
        const val TAG = "StoveBle"
        const val BOND_TIMEOUT_MS = 30_000L
        const val PAIRING_FAILED = "Pairing failed: put the stove panel in pairing mode and retry"
    }
}
