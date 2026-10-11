package com.mczadvanced.wear.protocol

import java.io.ByteArrayOutputStream

/**
 * Unsolicited status push on the notify characteristic: `## type frag payload`, fragment 01
 * starts, 02 ends. Never a multiple of 16 bytes, so it cannot be confused with a reply frame.
 */
class BroadcastAssembler {
    private val buffer = ByteArrayOutputStream()
    private var started = false

    /** Feeds one notification; returns register -> value once a broadcast is complete. */
    fun offer(packet: ByteArray): Map<Int, Int>? {
        if (!isBroadcast(packet)) return null
        when (packet.u8(3)) {
            1 -> {
                buffer.reset()
                started = true
            }
            2 -> if (!started) return null
            else -> return null
        }
        buffer.write(packet, 4, packet.size - 4)
        if (packet.u8(3) != 2) return null
        started = false
        val body = FrameCodec.decode(buffer.toByteArray()) ?: return null
        return (0 until body.size / 2).associate { STATUS_BASE + it to body.u16(2 * it) }
    }

    companion object {
        const val STATUS_BASE = 0x02BA

        fun isBroadcast(packet: ByteArray): Boolean =
            packet.size >= 4 && packet[0] == 0x23.toByte() && packet[1] == 0x23.toByte() && packet.size % 16 != 0
    }
}
