package com.mczadvanced.wear.protocol

enum class StoveMode(val code: Int, val label: String) {
    MANUAL(0, "Manual"),
    AUTO(1, "Auto"),
    OVERNIGHT(2, "Overnight"),
    COMFORT(3, "Comfort"),
    TURBO(4, "Turbo");

    companion object {
        fun of(code: Int?): StoveMode? = entries.firstOrNull { it.code == code }
    }
}

/** Last known stove values; null = never read. */
data class StoveState(
    val roomTemp: Float? = null,
    val setpoint: Float? = null,
    val fumesTemp: Float? = null,
    val stateCode: Int? = null,
    val phase: Int? = null,
    val mode: Int? = null,
    val powerLevel: Int? = null,
    val fan: Int? = null,
    val alarm: Int? = null,
) {
    /** True when burning, false when off or shutting down, null when unknown or transitional. */
    val isOn: Boolean?
        get() = when {
            stateCode == TURNING_OFF -> false
            phase == Registers.PHASE_OFF -> false
            phase == Registers.PHASE_ON -> true
            else -> null
        }

    /** The power toggle is only offered in a stable, known state. */
    val canToggle: Boolean get() = isOn != null && stateCode != TURNING_OFF

    val stateLabel: String
        get() = stateCode?.let { STATE_LABELS[it] } ?: when (isOn) {
            true -> "On"
            false -> "Off"
            null -> "--"
        }

    val modeLabel: String get() = StoveMode.of(mode)?.label ?: "--"

    val fanLabel: String
        get() = when (fan) {
            null -> "--"
            Registers.FAN_AUTO -> "Auto"
            else -> fan.toString()
        }

    val hasAlarm: Boolean get() = (alarm ?: 0) != 0

    companion object {
        const val TURNING_OFF = 0x0103

        val STATE_LABELS = mapOf(
            0x0000 to "Off",
            0x0101 to "Cleaning",
            0x0201 to "Loading",
            0x0301 to "Start 1",
            0x0401 to "Start 2",
            0x0501 to "Stabilization",
            0x0601 to "Anti-condensation",
            0x0202 to "On",
            TURNING_OFF to "Turning off",
        )
    }
}
