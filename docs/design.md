# Design — MCZ WearOS (Wear OS BLE stove controller)

## Goal

Standalone Wear OS app that controls an MCZ Maestro/Maestro+ pellet stove directly over
Bluetooth Low Energy — no phone, no MCZ cloud. Replaces the official app for daily use
from the wrist, plus a Tile for at-a-glance status.

### Success criteria

- From the watch: see room temperature, setpoint, stove state (Off / Cleaning / Loading /
  Start 1 / Start 2 / Stabilization / On / Turning off), mode, power level, fan level and
  active alarm.
- From the watch: turn stove on/off, change setpoint (±0.5 °C), mode, power level 1–5,
  fan level 1–5 / Auto.
- Tile shows last-known state instantly and refreshes when it becomes visible.
- Watch releases the BLE link when idle so the official app can still connect.

### Assumptions

- Single stove, sideloaded build (not on the Play Store).
- Wear OS 4+ watch (minSdk 33).
- Stove exposes the Maestro BLE module described below (advertises `MCZ_EP…`).

## Protocol (from reverse-engineering, reimplemented in Kotlin)

Reference: https://github.com/Houtmann/mcz-maestro-ble (ESP32 bridge, no license file —
protocol facts reused, no code copied).

- GATT service `0xABF0`; write (no response) on `0xABF1`; notifications on `0xABF2`.
- Bonding: "Just Works" (no MITM, no IO). The stove drops links from unbonded devices, and
  accepts a new bond only while its panel is in pairing mode (press **+** and **−** together,
  or power-cycle the stove). The app bonds from the pairing screen only, never in the
  background; `connect()` fails with "Stove not paired" when the bond is missing.
- Request MTU 517 (status frames exceed 20 bytes).
- Frame = AES-128-CBC(KEY1, IV0) of
  `[counter u32 LE][TOKEN 16 B][Modbus RTU PDU][CRC16 LE][PKCS#7 pad to 16]`.
  Key, IV and token are fixed constants (see `FrameCodec`). Counter starts at
  `0x6A418000` and increments per frame.
- Modbus RTU, slave `0x01`. CRC16 Modbus (poly `0xA001`, init `0xFFFF`).
  - fn `0x03` read: `01 03 regHi regLo cntHi cntLo` → reply `01 03 byteCount data…`.
    Reply carries no address: match it to the single outstanding request, reject if
    `byteCount != 2 × count`.
  - fn `0x06` write: `01 06 regHi regLo valHi valLo` → echo of the same PDU = accepted.
- Notifications starting `0x23 0x23` (`##`) with length not a multiple of 16 are module
  status broadcasts: `## type frag payload`, frag `01` starts, `02` ends; reassembled
  payload is an AES frame whose body is a register image starting at `0x02BA`.
  Decoded opportunistically into the same state.

### Registers used

| Reg | Access | Meaning |
|---|---|---|
| `0x02BC` | R | Room temp, °C ×10 |
| `0x02C5` | R | Flue gas temp, °C ×10 |
| `0x0320` | R | Fine state code (`0x0000` Off, `0x0101` Cleaning, `0x0201` Loading, `0x0301` Start 1, `0x0401` Start 2, `0x0501` Stabilization, `0x0601` Anti-condensation, `0x0202` On, `0x0103` Turning off) |
| `0x0322` | R | Coarse phase: 1 = off/standby, 3 = on |
| `0x0323` | R | Active alarm = low byte (0 = none) |
| `0x0324` | R | Live fan level |
| `0x032E` | R | Live mode |
| `0x038A` | W | **Power toggle** (write 1) |
| `0x03E9` | R/W | Mode: 0 Manual, 1 Auto, 2 Overnight, 3 Comfort, 4 Turbo |
| `0x03EB` | R/W | Power level 1–5 |
| `0x03F7` | R/W | Setpoint, °C ×10 (valid 5–45 °C) |
| `0x03FA` | R/W | Fan 1: 1–5 fixed, 6 = Auto |

Poll blocks per refresh: `0x02BC × 51`, `0x0320 × 77`, `0x03E9 × 20`, sequential, one
outstanding request at a time.

## Architecture

Single Gradle module `app` (Wear OS), package `com.mczadvanced.wear`. Kotlin, Jetpack
Compose for Wear OS (Material 3), Tiles via ProtoLayout, DataStore, coroutines.

```
protocol/   pure Kotlin, no Android deps — JVM unit tested
  ModbusCrc, ModbusPdu, FrameCodec, BroadcastAssembler, Registers,
  StoveState, StoveStateMapper
ble/        Android BLE
  StoveScanner, StoveBleClient (implements StoveTransport)
data/
  StoveTransport (interface: connect, read, write, close)
  StoveRepository, StateCache (DataStore), SettingsStore (saved MAC)
ui/         Compose screens + ViewModel
tile/       StoveTileService
```

### Units

- **`ModbusCrc` / `ModbusPdu`** — build read/write PDUs, parse fn 03/06 replies, validate CRC.
- **`FrameCodec`** — encrypt request PDU into frame with counter; decrypt frame, check
  token, strip padding, return PDU. AES via `javax.crypto` `AES/CBC/NoPadding` with manual
  PKCS#7.
- **`BroadcastAssembler`** — reassemble `##` fragments into a register image.
- **`StoveStateMapper`** — apply `(register, value)` pairs to immutable `StoveState`.
- **`StoveTransport`** — `suspend fun readRegisters(reg, count): IntArray`,
  `suspend fun writeRegister(reg, value)`, `val broadcasts: Flow<Map<Int,Int>>`,
  `connect()`, `close()`. Fake implementation for tests.
- **`StoveBleClient`** — scan (filter name prefix `MCZ_EP` or saved MAC, 8 s timeout),
  `connectGatt` with `autoConnect=false`, `createBond` if not bonded, MTU 517, discover,
  enable notifications on `ABF2` (CCCD), serialize requests with a `Mutex`, 3 s timeout
  per request; reads retried once, writes never retried (a repeated power toggle would
  flip the stove back).
- **`StoveRepository`** — single source of truth exposed as `StateFlow<StoveUiState>`
  (state + `lastUpdated` + connection status). `refresh()`, commands. Holds connection
  while used; auto-disconnect after 30 s without requests. Writes every successful state
  into `StateCache`.
  - **On/off safety:** `setOn(desired)` reads state and phase (`0x0320..0x0322`) in the
    same session right before writing. It toggles only from a stable state (phase 1 = off,
    phase 3 = on, not "Turning off"), refuses unknown or transitional phases, and refuses a
    second toggle within 60 s of the previous one. Never toggles from cached state.
  - After any write: wait for echo, then re-read the affected block.
- **UI**
  - `PairingScreen` — instructions ("press + and − on the stove display"), scan list,
    select → save MAC.
  - `MainScreen` — big room temp, state text, setpoint with −/+ buttons, On/Off button,
    chips for Mode / Power / Fan, alarm chip when alarm ≠ 0, "updated Xs ago".
  - `ModePicker`, `PowerPicker` (1–5), `FanPicker` (1–5, Auto).
  - `ConfirmPowerScreen` — deep-link target from Tile On/Off.
- **Tile** — `StoveTileService` renders cached state (room temp, setpoint, state,
  age). `onTileEnterEvent` launches refresh in service scope, then
  `getUpdater().requestUpdate()`. Refreshes are skipped when the data is younger than 10 s,
  so the update request cannot trigger a refresh loop. Tap body = refresh (LoadAction). Edge button
  "Turn on"/"Turn off" (LaunchAction → app `ConfirmPowerScreen`).

### Permissions

`BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT`; `uses-feature
android.hardware.bluetooth_le required=true`; `com.google.android.wearable.standalone=true`.

## Error handling

- Bluetooth off → prompt to enable.
- Permissions denied → explanation screen with retry.
- Stove not found / connect timeout / busy with another client → status "Stove
  unreachable", show cached data with age, retry button.
- Command not echoed within timeout → error message, state re-read, no optimistic UI.
- Corrupt / bad-CRC / token-mismatch frames → discarded silently (logged).
- Setpoint outside 5–45 °C rejected before sending.

## Testing

- **JVM unit tests (TDD):** CRC known vectors; PDU build/parse; FrameCodec round-trip
  and padding; BroadcastAssembler fragment handling; StateMapper; StoveRepository with
  `FakeTransport` (refresh, commands, on/off toggle safety, timeout handling).
- **Emulator:** UI + Tile rendering on Wear OS AVD (`Wear_OS_Large_Round_API_34`) with a
  debug-only fake transport (no BLE in emulator).
- **Real device:** install on the user's watch via ADB wireless debugging
  (`adb pair` / `adb connect`), manual test against the stove.
- **Build check:** `gradlew testDebugUnitTest assembleDebug`.

## Out of scope

Complication, periodic background refresh, multiple stoves, chrono/scheduler editing,
hydro (boiler/puffer) parameters, phone companion app, cloud.
