package com.mczadvanced.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoveStateMapperTest {
    @Test
    fun `maps known registers`() {
        val s = StoveStateMapper.apply(
            StoveState(),
            mapOf(
                Registers.ROOM_TEMP to 215, Registers.SETPOINT to 220, Registers.FUMES_TEMP to 1405,
                Registers.STATE to 0x0202, Registers.PHASE to 3, Registers.ALARM to 0x1205,
                Registers.MODE to 1, Registers.POWER_LEVEL to 3, Registers.FAN to 6,
            ),
        )
        assertEquals(StoveState(21.5f, 22.0f, 140.5f, 0x0202, 3, 1, 3, 6, 5), s)
        assertEquals(true, s.isOn)
        assertEquals("On", s.stateLabel)
        assertEquals("Auto", s.modeLabel)
        assertEquals("Auto", s.fanLabel)
        assertTrue(s.hasAlarm)
    }

    @Test
    fun `temperatures are signed and sentinel is null`() {
        val s = StoveStateMapper.apply(StoveState(), mapOf(Registers.ROOM_TEMP to 0xFFF6, Registers.SETPOINT to 0xFFFF))
        assertEquals(-1.0f, s.roomTemp)
        assertNull(s.setpoint)
    }

    @Test
    fun `unknown registers are ignored and other fields kept`() {
        val before = StoveState(roomTemp = 20f)
        assertEquals(before, StoveStateMapper.apply(before, mapOf(0x1234 to 9)))
    }

    @Test
    fun `phase off means stove off`() {
        val s = StoveState(phase = Registers.PHASE_OFF, stateCode = 0x0000)
        assertEquals(false, s.isOn)
        assertEquals("Off", s.stateLabel)
        assertFalse(s.hasAlarm)
    }

    @Test
    fun `labels fall back when unknown`() {
        assertEquals("--", StoveState().stateLabel)
        assertEquals("On", StoveState(phase = 3, stateCode = 0x7777).stateLabel)
        assertEquals("--", StoveState(mode = 9).modeLabel)
        assertEquals("3", StoveState(fan = 3).fanLabel)
    }

    @Test
    fun `registersFrom numbers values from start`() {
        assertEquals(mapOf(10 to 1, 11 to 2), StoveStateMapper.registersFrom(10, listOf(1, 2)))
    }

    @Test
    fun `codec round trips full and empty state`() {
        val full = StoveState(21.5f, 22f, 140.5f, 0x0202, 3, 1, 3, 6, 0)
        assertEquals(full, StoveStateCodec.decode(StoveStateCodec.encode(full)))
        assertEquals(StoveState(), StoveStateCodec.decode(StoveStateCodec.encode(StoveState())))
    }

    @Test
    fun `codec tolerates missing or garbage text`() {
        assertEquals(StoveState(), StoveStateCodec.decode(null))
        assertEquals(StoveState(), StoveStateCodec.decode("garbage"))
    }
}
