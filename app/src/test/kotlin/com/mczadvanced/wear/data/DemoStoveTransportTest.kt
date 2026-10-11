package com.mczadvanced.wear.data

import com.mczadvanced.wear.protocol.Registers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DemoStoveTransportTest {
    @Test
    fun `toggle flips phase and state code`() = runTest {
        val demo = DemoStoveTransport()
        demo.connect()
        val before = demo.readRegisters(Registers.PHASE, 1).single()
        demo.writeRegister(Registers.POWER_TOGGLE, 1)
        val after = demo.readRegisters(Registers.PHASE, 1).single()
        assertEquals(before == Registers.PHASE_OFF, after != Registers.PHASE_OFF)
        demo.writeRegister(Registers.POWER_TOGGLE, 1)  // restore shared demo state
    }

    @Test
    fun `writes are read back`() = runTest {
        val demo = DemoStoveTransport()
        demo.writeRegister(Registers.SETPOINT, 230)
        assertEquals(listOf(230), demo.readRegisters(Registers.SETPOINT, 1))
    }
}
