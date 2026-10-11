package com.mczadvanced.wear.protocol

/** Compact text form of [StoveState] for the DataStore cache. */
object StoveStateCodec {
    private const val FIELDS = 9

    fun encode(s: StoveState): String =
        listOf(s.roomTemp, s.setpoint, s.fumesTemp, s.stateCode, s.phase, s.mode, s.powerLevel, s.fan, s.alarm)
            .joinToString(";") { it?.toString() ?: "" }

    fun decode(text: String?): StoveState {
        val p = text?.split(";") ?: return StoveState()
        if (p.size != FIELDS) return StoveState()
        return StoveState(
            roomTemp = p[0].toFloatOrNull(),
            setpoint = p[1].toFloatOrNull(),
            fumesTemp = p[2].toFloatOrNull(),
            stateCode = p[3].toIntOrNull(),
            phase = p[4].toIntOrNull(),
            mode = p[5].toIntOrNull(),
            powerLevel = p[6].toIntOrNull(),
            fan = p[7].toIntOrNull(),
            alarm = p[8].toIntOrNull(),
        )
    }
}
