package com.mczadvanced.wear.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModbusPduTest {
    @Test
    fun `read request layout`() {
        assertEquals("010302bc0033", ModbusPdu.readRequest(0x02BC, 51).toHex())
    }

    @Test
    fun `write request layout`() {
        assertEquals("010603f700d7", ModbusPdu.writeRequest(0x03F7, 215).toHex())
    }

    @Test
    fun `appendCrc adds crc low byte first`() {
        assertEquals("01030000000ac5cd", ModbusPdu.appendCrc(hexBytes("01030000000a")).toHex())
    }

    @Test
    fun `parse read reply`() {
        val reply = ModbusPdu.appendCrc(hexBytes("01030400d70003"))
        assertEquals(ModbusResponse.ReadValues(listOf(215, 3)), ModbusPdu.parse(reply))
    }

    @Test
    fun `parse write echo`() {
        val reply = ModbusPdu.appendCrc(ModbusPdu.writeRequest(0x038A, 1))
        assertEquals(ModbusResponse.WriteEcho(0x038A, 1), ModbusPdu.parse(reply))
    }

    @Test
    fun `parse exception reply`() {
        val reply = ModbusPdu.appendCrc(hexBytes("018602"))
        assertEquals(ModbusResponse.Error(0x06, 0x02), ModbusPdu.parse(reply))
    }

    @Test
    fun `parse rejects bad crc`() {
        val reply = ModbusPdu.appendCrc(hexBytes("01030400d70003"))
        reply[reply.size - 1] = (reply[reply.size - 1] + 1).toByte()
        assertNull(ModbusPdu.parse(reply))
    }

    @Test
    fun `parse rejects byte count that does not match length`() {
        assertNull(ModbusPdu.parse(ModbusPdu.appendCrc(hexBytes("01030600d70003"))))
    }

    @Test
    fun `parse rejects too short frame`() {
        assertNull(ModbusPdu.parse(hexBytes("0103")))
    }

    @Test
    fun `read reply answers read request with same count`() {
        val req = ModbusPdu.readRequest(0x02BC, 2)
        assertTrue(ModbusPdu.isReplyTo(req, ModbusResponse.ReadValues(listOf(1, 2))))
    }

    @Test
    fun `read reply with wrong register count does not answer request`() {
        val req = ModbusPdu.readRequest(0x0320, 77)
        assertFalse(ModbusPdu.isReplyTo(req, ModbusResponse.ReadValues(List(51) { 0 })))
    }

    @Test
    fun `write echo must match register and value`() {
        val req = ModbusPdu.writeRequest(0x03F7, 215)
        assertTrue(ModbusPdu.isReplyTo(req, ModbusResponse.WriteEcho(0x03F7, 215)))
        assertFalse(ModbusPdu.isReplyTo(req, ModbusResponse.WriteEcho(0x03F7, 220)))
        assertFalse(ModbusPdu.isReplyTo(req, ModbusResponse.ReadValues(listOf(215))))
    }

    @Test
    fun `error reply answers request of same function`() {
        assertTrue(ModbusPdu.isReplyTo(ModbusPdu.writeRequest(1, 1), ModbusResponse.Error(0x06, 2)))
        assertFalse(ModbusPdu.isReplyTo(ModbusPdu.readRequest(1, 1), ModbusResponse.Error(0x06, 2)))
    }
}
