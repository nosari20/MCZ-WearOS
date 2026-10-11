package com.mczadvanced.wear.data

import com.mczadvanced.wear.protocol.Registers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeTransport(val registers: MutableMap<Int, Int> = mutableMapOf()) : StoveTransport {
    val writes = mutableListOf<Pair<Int, Int>>()
    val reads = mutableListOf<Pair<Int, Int>>()
    var connects = 0
    var closes = 0
    var failConnect = false
    var hangConnect = false
    var failReads = false
    var connected = false
    override val broadcasts = MutableSharedFlow<Map<Int, Int>>(extraBufferCapacity = 8)
    override val isConnected: Boolean get() = connected

    override suspend fun connect() {
        connects++
        if (hangConnect) awaitCancellation()
        if (failConnect) throw StoveException("Stove unreachable")
        connected = true
    }

    override suspend fun readRegisters(start: Int, count: Int): List<Int> {
        if (failReads) throw StoveException("No reply from stove")
        reads += start to count
        return List(count) { registers[start + it] ?: 0 }
    }

    override suspend fun writeRegister(register: Int, value: Int) {
        writes += register to value
        if (register == Registers.POWER_TOGGLE) {
            registers[Registers.PHASE] = if (registers[Registers.PHASE] == Registers.PHASE_OFF) 3 else Registers.PHASE_OFF
        } else {
            registers[register] = value
        }
    }

    override suspend fun close() {
        closes++
        connected = false
    }
}
