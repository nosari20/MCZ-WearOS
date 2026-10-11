package com.mczadvanced.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class ModbusCrcTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `crc of read request matches reference vector`() {
        assertEquals(0xCDC5, ModbusCrc.compute(bytes(0x01, 0x03, 0x00, 0x00, 0x00, 0x0A)))
    }

    @Test
    fun `crc of write request matches reference vector`() {
        assertEquals(0x0B98, ModbusCrc.compute(bytes(0x01, 0x06, 0x00, 0x01, 0x00, 0x03)))
    }

    @Test
    fun `crc honours offset and length`() {
        val padded = bytes(0xFF, 0x01, 0x03, 0x00, 0x00, 0x00, 0x0A, 0xFF)
        assertEquals(0xCDC5, ModbusCrc.compute(padded, 1, 6))
    }
}
