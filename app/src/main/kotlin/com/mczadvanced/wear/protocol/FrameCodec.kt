package com.mczadvanced.wear.protocol

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Maestro BLE frame: AES-128-CBC(KEY, IV) over
 * `[counter u32 LE][TOKEN 16 B][body][PKCS#7]`. Constants from the reverse-engineered
 * module firmware (github.com/Houtmann/mcz-maestro-ble).
 */
class FrameCodec(startCounter: Int = START_COUNTER) {
    private var counter = startCounter

    /** Wraps a Modbus PDU (without CRC) into an encrypted frame. */
    @Synchronized
    fun encode(pdu: ByteArray): ByteArray = encrypt(counter++, ModbusPdu.appendCrc(pdu))

    companion object {
        const val START_COUNTER = 0x6A418000
        private val KEY = hexBytes("6e296b0bbb1d43f36e47f72e7b6f2e77")
        private val IV = hexBytes("da1a557349f25c641b1a368af5b218a7")
        private val TOKEN = hexBytes("31dd34512639377b05a2510de725fc75")
        private const val HEADER = 20

        fun encrypt(counter: Int, body: ByteArray): ByteArray {
            val size = HEADER + body.size
            val pad = 16 - size % 16
            val plain = ByteArray(size + pad)
            for (i in 0 until 4) plain[i] = (counter ushr (8 * i)).toByte()
            TOKEN.copyInto(plain, 4)
            body.copyInto(plain, HEADER)
            plain.fill(pad.toByte(), size)
            return cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
        }

        /** Body after counter and token, padding stripped; null if not a valid frame. */
        fun decode(frame: ByteArray): ByteArray? {
            if (frame.size < 32 || frame.size % 16 != 0) return null
            val plain = cipher(Cipher.DECRYPT_MODE).doFinal(frame)
            if (!plain.copyOfRange(4, HEADER).contentEquals(TOKEN)) return null
            val pad = plain.u8(plain.size - 1)
            val end = if (pad in 1..16) plain.size - pad else plain.size
            if (end < HEADER) return null
            return plain.copyOfRange(HEADER, end)
        }

        private fun cipher(mode: Int): Cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
            init(mode, SecretKeySpec(KEY, "AES"), IvParameterSpec(IV))
        }
    }
}
