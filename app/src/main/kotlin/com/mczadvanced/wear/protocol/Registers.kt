package com.mczadvanced.wear.protocol

/** Maestro Modbus registers (verified on stoves by github.com/Houtmann/mcz-maestro-ble). */
object Registers {
    const val ROOM_TEMP = 0x02BC     // °C x10
    const val FUMES_TEMP = 0x02C5    // °C x10
    const val STATE = 0x0320         // fine state code, see StoveState.STATE_LABELS
    const val PHASE = 0x0322         // 1 = off/standby, 3 = on (incl. start sequence)
    const val ALARM = 0x0323         // low byte = active alarm, 0 = none
    const val POWER_TOGGLE = 0x038A  // write 1 = press power button (toggle!)
    const val MODE = 0x03E9          // StoveMode code
    const val POWER_LEVEL = 0x03EB   // 1..5
    const val SETPOINT = 0x03F7      // °C x10
    const val FAN = 0x03FA           // 1..5, 6 = auto

    const val PHASE_OFF = 1
    const val FAN_AUTO = 6
    const val SETPOINT_MIN = 5f
    const val SETPOINT_MAX = 45f

    val STATE_BLOCK = 0x0320 to 77
    val SETTINGS_BLOCK = 0x03E9 to 20
    val POLL_BLOCKS = listOf(0x02BC to 51, STATE_BLOCK, SETTINGS_BLOCK)
}
