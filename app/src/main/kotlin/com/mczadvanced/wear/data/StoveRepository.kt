package com.mczadvanced.wear.data

import com.mczadvanced.wear.protocol.Registers
import com.mczadvanced.wear.protocol.StoveMode
import com.mczadvanced.wear.protocol.StoveState
import com.mczadvanced.wear.protocol.StoveStateMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

enum class LinkStatus { IDLE, CONNECTING, CONNECTED, UNREACHABLE }

data class StoveUiState(
    val stove: StoveState = StoveState(),
    val updatedAt: Long? = null,
    val link: LinkStatus = LinkStatus.IDLE,
    val error: String? = null,
    val busy: Boolean = false,
)

data class CachedStove(val state: StoveState, val updatedAt: Long)

interface StoveCache {
    suspend fun load(): CachedStove?
    suspend fun save(value: CachedStove)
}

/**
 * Single source of truth for the stove. Opens the link on demand, serializes every exchange,
 * and closes the link after [idleTimeoutMs] so the official app can connect too.
 */
class StoveRepository(
    private val transportFactory: suspend () -> StoveTransport?,
    private val cache: StoveCache,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idleTimeoutMs: Long = 30_000,
) {
    private val _state = MutableStateFlow(StoveUiState())
    val state: StateFlow<StoveUiState> = _state.asStateFlow()

    private val lock = Mutex()
    private var transport: StoveTransport? = null
    private var broadcastJob: Job? = null
    private var idleJob: Job? = null
    private var cacheLoaded = false

    suspend fun loadCache() {
        if (cacheLoaded) return
        cacheLoaded = true
        val cached = cache.load() ?: return
        _state.update { if (it.updatedAt == null) it.copy(stove = cached.state, updatedAt = cached.updatedAt) else it }
    }

    suspend fun refresh(): Boolean = session { t ->
        for ((start, count) in Registers.POLL_BLOCKS) poll(t, start, count)
    }

    /** The power register is a toggle: the phase is read in the same session right before writing. */
    suspend fun setOn(on: Boolean): Boolean = session { t ->
        val phase = t.readRegisters(Registers.PHASE, 1).single()
        apply(mapOf(Registers.PHASE to phase))
        if ((phase != Registers.PHASE_OFF) != on) {
            t.writeRegister(Registers.POWER_TOGGLE, 1)
            poll(t, Registers.STATE_BLOCK.first, Registers.STATE_BLOCK.second)
        }
    }

    suspend fun setSetpoint(celsius: Float): Boolean {
        val rounded = (celsius * 2).roundToInt() / 2f
        if (rounded < Registers.SETPOINT_MIN || rounded > Registers.SETPOINT_MAX) return false
        return write(Registers.SETPOINT, (rounded * 10).roundToInt())
    }

    suspend fun setMode(mode: StoveMode): Boolean = write(Registers.MODE, mode.code)

    suspend fun setPowerLevel(level: Int): Boolean =
        if (level in 1..5) write(Registers.POWER_LEVEL, level) else false

    suspend fun setFan(level: Int): Boolean =
        if (level in 1..Registers.FAN_AUTO) write(Registers.FAN, level) else false

    /** Forget the current stove (used when pairing another one). */
    suspend fun reset() {
        lock.withLock {
            idleJob?.cancel()
            closeTransport()
            _state.value = StoveUiState()
        }
    }

    private suspend fun write(register: Int, value: Int): Boolean = session { t ->
        t.writeRegister(register, value)
        apply(mapOf(register to value))
        poll(t, Registers.SETTINGS_BLOCK.first, Registers.SETTINGS_BLOCK.second)
    }

    private suspend fun session(block: suspend (StoveTransport) -> Unit): Boolean = lock.withLock {
        idleJob?.cancel()
        _state.update { it.copy(busy = true, error = null) }
        try {
            block(transport ?: open())
            _state.update { it.copy(link = LinkStatus.CONNECTED) }
            cache.save(CachedStove(_state.value.stove, _state.value.updatedAt ?: clock()))
            true
        } catch (e: StoveException) {
            closeTransport()
            _state.update { it.copy(link = LinkStatus.UNREACHABLE, error = e.message) }
            false
        } finally {
            _state.update { it.copy(busy = false) }
            scheduleIdle()
        }
    }

    private suspend fun open(): StoveTransport {
        _state.update { it.copy(link = LinkStatus.CONNECTING) }
        val t = transportFactory() ?: throw StoveException("No stove paired")
        try {
            t.connect()
        } catch (e: StoveException) {
            runCatching { t.close() }
            throw e
        }
        transport = t
        broadcastJob = scope.launch {
            t.broadcasts.collect { registers ->
                apply(registers)
                cache.save(CachedStove(_state.value.stove, clock()))
            }
        }
        return t
    }

    private suspend fun poll(t: StoveTransport, start: Int, count: Int) {
        apply(StoveStateMapper.registersFrom(start, t.readRegisters(start, count)))
    }

    private fun apply(registers: Map<Int, Int>) {
        _state.update { it.copy(stove = StoveStateMapper.apply(it.stove, registers), updatedAt = clock()) }
    }

    private fun scheduleIdle() {
        if (transport == null) return
        idleJob = scope.launch {
            delay(idleTimeoutMs)
            lock.withLock { closeTransport() }
            _state.update { if (it.link == LinkStatus.CONNECTED) it.copy(link = LinkStatus.IDLE) else it }
        }
    }

    private suspend fun closeTransport() {
        broadcastJob?.cancel()
        broadcastJob = null
        transport?.let { runCatching { it.close() } }
        transport = null
    }
}
