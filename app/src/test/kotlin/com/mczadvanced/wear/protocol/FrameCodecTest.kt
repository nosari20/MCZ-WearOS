package com.mczadvanced.wear.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FrameCodecTest {
    private val kat = "bf959888b7f0802f8ce36525d9d53ddf87bb1e1758322116a7cc10e00e1c341d"

    @Test
    fun `encode matches known answer vector`() {
        assertEquals(kat, FrameCodec().encode(ModbusPdu.readRequest(0x02BC, 51)).toHex())
    }

    @Test
    fun `decode known answer vector returns pdu with crc`() {
        val expected = ModbusPdu.appendCrc(ModbusPdu.readRequest(0x02BC, 51))
        assertArrayEquals(expected, FrameCodec.decode(hexBytes(kat)))
    }

    @Test
    fun `counter increments per frame`() {
        val codec = FrameCodec()
        val pdu = ModbusPdu.readRequest(0x02BC, 51)
        val first = codec.encode(pdu)
        val second = codec.encode(pdu)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(ModbusPdu.appendCrc(pdu), FrameCodec.decode(second))
    }

    @Test
    fun `body filling a whole block gets a full padding block`() {
        val body = ByteArray(12) { it.toByte() }
        val frame = FrameCodec.encrypt(7, body)
        assertEquals(48, frame.size)
        assertArrayEquals(body, FrameCodec.decode(frame))
    }

    @Test
    fun `decode rejects length not multiple of 16`() {
        assertNull(FrameCodec.decode(hexBytes(kat).copyOf(31)))
    }

    @Test
    fun `decode rejects frame shorter than two blocks`() {
        assertNull(FrameCodec.decode(ByteArray(16)))
    }

    @Test
    fun `decode rejects wrong token`() {
        assertNull(FrameCodec.decode(ByteArray(32) { it.toByte() }))
    }
}
