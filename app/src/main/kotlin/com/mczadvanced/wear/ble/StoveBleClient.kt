package com.mczadvanced.wear.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.mczadvanced.wear.data.StoveException
import com.mczadvanced.wear.data.StoveTransport
import com.mczadvanced.wear.protocol.BroadcastAssembler
import com.mczadvanced.wear.protocol.FrameCodec
import com.mczadvanced.wear.protocol.ModbusPdu
import com.mczadvanced.wear.protocol.ModbusResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Maestro module over GATT: encrypted Modbus frames written to ABF1, replies and status
 * broadcasts notified on ABF2. One request in flight at a time.
 */
@SuppressLint("MissingPermission")
class StoveBleClient(private val context: Context, private val address: String) : StoveTransport {
    private val codec = FrameCodec()
    private val assembler = BroadcastAssembler()
    private val io = Mutex()

    /** Parsed replies; `null` signals that the link dropped. */
    private val replies = Channel<ModbusResponse?>(Channel.UNLIMITED)
    private val _broadcasts = MutableSharedFlow<Map<Int, Int>>(extraBufferCapacity = 4)
    override val broadcasts: Flow<Map<Int, Int>> = _broadcasts.asSharedFlow()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var writeChar: BluetoothGattCharacteristic? = null
    @Volatile private var ready: CompletableDeferred<Boolean>? = null
    @Volatile private var writeDone: CompletableDeferred<Boolean>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.requestMtu(MTU)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected (status $status)")
                ready?.complete(false)
                writeDone?.complete(false)
                replies.trySend(null)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU $mtu (status $status)")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(SERVICE)
            val write = service?.getCharacteristic(WRITE_CHAR)
            val notify = service?.getCharacteristic(NOTIFY_CHAR)
            if (write == null || notify == null) {
                Log.w(TAG, "Maestro service missing: ${g.services.map { it.uuid }}")
                ready?.complete(false)
                return
            }
            writeChar = write
            g.setCharacteristicNotification(notify, true)
            val cccd = notify.getDescriptor(CCCD)
            if (cccd == null) {
                ready?.complete(true)
            } else {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            ready?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            writeDone?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (BroadcastAssembler.isBroadcast(value)) {
                assembler.offer(value)?.let { _broadcasts.tryEmit(it) }
                return
            }
            val reply = FrameCodec.decode(value)?.let(ModbusPdu::parse)
            if (reply == null) Log.w(TAG, "Dropped notification of ${value.size} bytes") else replies.trySend(reply)
        }
    }

    override suspend fun connect() = io.withLock {
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: throw StoveException("Bluetooth unavailable")
            if (!adapter.isEnabled) throw StoveException("Bluetooth is off")
            val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
                ?: throw StoveException("Invalid stove address")
            if (device.bondState == BluetoothDevice.BOND_NONE) bond(device)
            drainReplies()
            val done = CompletableDeferred<Boolean>().also { ready = it }
            @Suppress("DEPRECATION") // deprecated in SDK 37; still the only call available down to minSdk 33
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            val ok = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { done.await() } ?: false
            if (!ok) {
                closeGatt()
                throw StoveException("Stove unreachable")
            }
        } catch (e: SecurityException) {
            closeGatt()
            throw StoveException("Bluetooth permission missing")
        }
    }

    override suspend fun readRegisters(start: Int, count: Int): List<Int> = io.withLock {
        val reply = exchange(ModbusPdu.readRequest(start, count), attempts = 2)
        (reply as? ModbusResponse.ReadValues)?.values
            ?: throw StoveException("Stove rejected read 0x%04X".format(start))
    }

    override suspend fun writeRegister(register: Int, value: Int) = io.withLock {
        // Never retried: repeating a power toggle would switch the stove back.
        val reply = exchange(ModbusPdu.writeRequest(register, value), attempts = 1)
        if (reply !is ModbusResponse.WriteEcho) throw StoveException("Stove rejected write 0x%04X".format(register))
    }

    override suspend fun close() = io.withLock { closeGatt() }

    private suspend fun exchange(request: ByteArray, attempts: Int): ModbusResponse {
        repeat(attempts) {
            drainReplies()
            send(codec.encode(request))
            val reply = withTimeoutOrNull(REPLY_TIMEOUT_MS) { awaitReply(request) }
            if (reply != null) return reply
        }
        throw StoveException("No reply from stove")
    }

    private fun drainReplies() {
        while (replies.tryReceive().isSuccess) continue
    }

    /** Skips late replies to earlier requests (read replies carry no address). */
    private suspend fun awaitReply(request: ByteArray): ModbusResponse {
        while (true) {
            val r = replies.receive() ?: throw StoveException("Connection lost")
            if (ModbusPdu.isReplyTo(request, r)) return r
            Log.w(TAG, "Ignored unrelated reply $r")
        }
    }

    private suspend fun send(frame: ByteArray) {
        val g = gatt ?: throw StoveException("Not connected")
        val c = writeChar ?: throw StoveException("Not connected")
        val done = CompletableDeferred<Boolean>().also { writeDone = it }
        val status = g.writeCharacteristic(c, frame, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        if (status != BluetoothStatusCodes.SUCCESS) throw StoveException("Write failed ($status)")
        withTimeoutOrNull(WRITE_TIMEOUT_MS) { done.await() }
    }

    /** "Just works" bonding; the stove display must be in pairing mode (press + and -). */
    private suspend fun bond(device: BluetoothDevice) {
        val result = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (d?.address != device.address) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                if (state != BluetoothDevice.BOND_BONDING) result.complete(state)
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            Context.RECEIVER_EXPORTED,
        )
        try {
            if (!device.createBond()) return
            val state = withTimeoutOrNull(BOND_TIMEOUT_MS) { result.await() }
            Log.i(TAG, "Bond result $state")
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun closeGatt() {
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        gatt = null
        writeChar = null
    }

    private companion object {
        const val TAG = "StoveBle"
        const val MTU = 517
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REPLY_TIMEOUT_MS = 3_000L
        const val WRITE_TIMEOUT_MS = 1_000L
        const val BOND_TIMEOUT_MS = 30_000L
        val SERVICE: UUID = UUID.fromString("0000abf0-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR: UUID = UUID.fromString("0000abf1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHAR: UUID = UUID.fromString("0000abf2-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
