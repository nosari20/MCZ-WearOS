package com.mczadvanced.wear.protocol

object StoveStateMapper {
    fun apply(state: StoveState, registers: Map<Int, Int>): StoveState {
        var s = state
        for ((register, raw) in registers) {
            s = when (register) {
                Registers.ROOM_TEMP -> s.copy(roomTemp = temp(raw))
                Registers.FUMES_TEMP -> s.copy(fumesTemp = temp(raw))
                Registers.SETPOINT -> s.copy(setpoint = temp(raw))
                Registers.STATE -> s.copy(stateCode = raw)
                Registers.PHASE -> s.copy(phase = raw)
                Registers.ALARM -> s.copy(alarm = raw and 0xFF)
                Registers.MODE -> s.copy(mode = raw)
                Registers.POWER_LEVEL -> s.copy(powerLevel = raw)
                Registers.FAN -> s.copy(fan = raw)
                else -> s
            }
        }
        return s
    }

    fun registersFrom(start: Int, values: List<Int>): Map<Int, Int> =
        values.withIndex().associate { (i, v) -> start + i to v }

    private fun temp(raw: Int): Float? = if (raw == 0xFFFF) null else raw.toShort() / 10f
}
