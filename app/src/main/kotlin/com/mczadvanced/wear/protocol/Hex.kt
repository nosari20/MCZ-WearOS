package com.mczadvanced.wear.protocol

fun hexBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF

/** Big-endian 16-bit value at [i]. */
internal fun ByteArray.u16(i: Int): Int = (u8(i) shl 8) or u8(i + 1)
