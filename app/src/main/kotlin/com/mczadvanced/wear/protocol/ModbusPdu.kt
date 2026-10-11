package com.mczadvanced.wear.protocol

sealed interface ModbusResponse {
    data class ReadValues(val values: List<Int>) : ModbusResponse
    data class WriteEcho(val register: Int, val value: Int) : ModbusResponse
    /** Modbus exception reply: [function] without the 0x80 flag. */
    data class Error(val function: Int, val code: Int) : ModbusResponse
}

/** Modbus RTU PDUs for slave 0x01. Requests are returned without CRC; [FrameCodec] appends it. */
object ModbusPdu {
    private const val SLAVE = 0x01
    const val READ = 0x03
    const val WRITE = 0x06

    fun readRequest(register: Int, count: Int): ByteArray {
        require(count in 1..125) { "count $count" }
        return pdu(READ, register, count)
    }

    fun writeRequest(register: Int, value: Int): ByteArray = pdu(WRITE, register, value and 0xFFFF)

    fun appendCrc(pdu: ByteArray): ByteArray {
        val crc = ModbusCrc.compute(pdu)
        return pdu + byteArrayOf(crc.toByte(), (crc ushr 8).toByte())
    }

    /** Parses a reply (PDU + CRC). Null when the CRC or the layout is invalid. */
    fun parse(frame: ByteArray): ModbusResponse? {
        if (frame.size < 5) return null
        val n = frame.size - 2
        val crc = frame.u8(n) or (frame.u8(n + 1) shl 8)
        if (ModbusCrc.compute(frame, 0, n) != crc) return null
        val function = frame.u8(1)
        return when {
            function == READ -> {
                val byteCount = frame.u8(2)
                if (byteCount % 2 != 0 || 3 + byteCount != n) null
                else ModbusResponse.ReadValues(List(byteCount / 2) { frame.u16(3 + 2 * it) })
            }
            function == WRITE && n == 6 -> ModbusResponse.WriteEcho(frame.u16(2), frame.u16(4))
            function and 0x80 != 0 && n == 3 -> ModbusResponse.Error(function and 0x7F, frame.u8(2))
            else -> null
        }
    }

    /**
     * Read replies carry no address, so a reply is matched by function and size (read) or by
     * exact echo (write). Anything else is a late reply to an earlier request.
     */
    fun isReplyTo(request: ByteArray, response: ModbusResponse): Boolean {
        val function = request.u8(1)
        return when (response) {
            is ModbusResponse.ReadValues -> function == READ && response.values.size == request.u16(4)
            is ModbusResponse.WriteEcho ->
                function == WRITE && response.register == request.u16(2) && response.value == request.u16(4)
            is ModbusResponse.Error -> response.function == function
        }
    }

    private fun pdu(function: Int, a: Int, b: Int) = byteArrayOf(
        SLAVE.toByte(), function.toByte(),
        (a ushr 8).toByte(), a.toByte(),
        (b ushr 8).toByte(), b.toByte(),
    )
}
