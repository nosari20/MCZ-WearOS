package com.mczadvanced.wear.data

import com.mczadvanced.wear.protocol.Registers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Simulated stove for the emulator (no BLE there). Selected by pairing the "DEMO" address. */
class DemoStoveTransport : StoveTransport {
    override val broadcasts: Flow<Map<Int, Int>> = emptyFlow()
    override val isConnected = true

    override suspend fun connect() = delay(600)

    override suspend fun readRegisters(start: Int, count: Int): List<Int> {
        delay(120)
        return synchronized(registers) { List(count) { registers[start + it] ?: 0 } }
    }

    override suspend fun writeRegister(register: Int, value: Int) {
        delay(120)
        synchronized(registers) {
            if (register == Registers.POWER_TOGGLE) {
                val on = registers[Registers.PHASE] == Registers.PHASE_OFF
                registers[Registers.PHASE] = if (on) 3 else Registers.PHASE_OFF
                registers[Registers.STATE] = if (on) 0x0202 else 0x0000
            } else {
                registers[register] = value
            }
        }
    }

    override suspend fun close() = Unit

    companion object {
        const val ADDRESS = "DEMO"

        // Shared so the simulated stove keeps its state between sessions.
        private val registers = mutableMapOf(
            Registers.ROOM_TEMP to 205, Registers.FUMES_TEMP to 450, Registers.STATE to 0x0000,
            Registers.PHASE to Registers.PHASE_OFF, Registers.ALARM to 0, Registers.MODE to 0,
            Registers.POWER_LEVEL to 3, Registers.SETPOINT to 215, Registers.FAN to Registers.FAN_AUTO,
        )
    }
}
