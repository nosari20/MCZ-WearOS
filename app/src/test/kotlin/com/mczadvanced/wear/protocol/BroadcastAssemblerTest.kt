package com.mczadvanced.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastAssemblerTest {
    // Registers 0x02BA, 0x02BB, 0x02BC = 0, 0, 215 (room 21.5 °C).
    private val cipher = FrameCodec.encrypt(1, hexBytes("0000000000d7"))  // 32 bytes
    private fun fragment(frag: Int, part: ByteArray) = byteArrayOf(0x23, 0x23, 0x02, frag.toByte()) + part
    private val frag1 = fragment(1, cipher.copyOfRange(0, 10))   // 14 bytes
    private val frag2 = fragment(2, cipher.copyOfRange(10, 32))  // 26 bytes

    @Test
    fun `two fragments decode into register image from status base`() {
        val assembler = BroadcastAssembler()
        assertNull(assembler.offer(frag1))
        assertEquals(mapOf(0x02BA to 0, 0x02BB to 0, 0x02BC to 215), assembler.offer(frag2))
    }

    @Test
    fun `second fragment without first is dropped`() {
        assertNull(BroadcastAssembler().offer(frag2))
    }

    @Test
    fun `new first fragment restarts reassembly`() {
        val assembler = BroadcastAssembler()
        assembler.offer(fragment(1, ByteArray(9)))
        assembler.offer(frag1)
        assertEquals(215, assembler.offer(frag2)?.get(0x02BC))
    }

    @Test
    fun `aes reply frame is not a broadcast`() {
        val reply = FrameCodec().encode(ModbusPdu.readRequest(0x02BC, 1))
        assertFalse(BroadcastAssembler.isBroadcast(reply))
        assertNull(BroadcastAssembler().offer(reply))
        assertTrue(BroadcastAssembler.isBroadcast(frag1))
    }
}
