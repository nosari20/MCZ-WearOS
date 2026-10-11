package com.mczadvanced.wear.data

import kotlinx.coroutines.flow.Flow

class StoveException(message: String) : Exception(message)

/** One link to the stove. Every failure is reported as [StoveException]. */
interface StoveTransport {
    /** Register images the stove pushes on its own. */
    val broadcasts: Flow<Map<Int, Int>>

    /** False once the link dropped; the repository then reconnects before the next command. */
    val isConnected: Boolean

    suspend fun connect()
    suspend fun readRegisters(start: Int, count: Int): List<Int>

    /** Returns once the stove echoed the write. Never retried by implementations. */
    suspend fun writeRegister(register: Int, value: Int)
    suspend fun close()
}
