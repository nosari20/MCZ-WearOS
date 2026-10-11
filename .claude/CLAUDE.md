# MCZ WearOS — project guide for Claude

"Stove Remote" (store title "Stove Remote for Wear OS"): standalone Wear OS app + Tile controlling an MCZ Maestro pellet stove over BLE
(AES-128-CBC encrypted Modbus RTU). Single Gradle module `app`, package
`com.mczadvanced.wear`. Design: `docs/design.md`. User docs: `README.md`.

## Build and test

```bash
./gradlew :app:testDebugUnitTest            # JVM unit tests (protocol + repository)
./gradlew :app:assembleDebug                # debug APK (has the Demo stove)
./gradlew :app:assembleRelease              # R8 build — the one to put on a real watch
./gradlew :app:bundleRelease                # Play upload (.aab); see docs/google-play.md
```

- Toolchain: AGP 9 (built-in Kotlin), Gradle 9.8, minSdk 33, compileSdk/targetSdk 37,
  versions in `gradle/libs.versions.toml`.
- On Windows run Gradle with the Android Studio JBR:
  `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (Git Bash).
- If Gradle fails with "Unable to delete directory … build", run `./gradlew --stop`, delete
  `app/build`, and retry. It is a Windows file lock, not a code problem.

## Layout

| Package | Role | Android deps |
|---|---|---|
| `protocol` | CRC, Modbus PDUs, AES frame codec, broadcast reassembly, registers, `StoveState` | none |
| `data` | `StoveTransport` interface, `StoveRepository`, DataStore stores, `DemoStoveTransport` | DataStore only in `Stores.kt` |
| `ble` | `StoveBleClient` (GATT transport), `StoveScanner`, `StoveBonding` | yes |
| `ui` | Compose for Wear OS screens, `MainActivity`, navigation | yes |
| `tile` | ProtoLayout Material 3 tile | yes |

Keep `protocol` and `data` (except `Stores.kt`) free of Android APIs so they stay JVM-testable.

## Safety rules (the stove is a heating appliance)

- Register `0x038A` is a power **toggle**. Only `StoveRepository.setOn` may write it: it reads
  state + phase in the same session, toggles only from a stable state (phase 1 off / phase 3 on,
  not "Turning off"), and enforces a 60 s cooldown. Do not add other writers.
- **Never retry writes** (`StoveBleClient.writeRegister` uses one attempt). Reads may retry.
- Every user-initiated stove command goes through a confirmation or an explicit control; the
  app must never send commands on its own (refresh = reads only).
- BLE work started from UI runs in `StoveApp.appScope`, never in a composition scope, so
  navigation cannot cancel it mid-exchange and leave the stove's single link busy.
- Bonding happens only from the pairing screen (`StoveBonding`); `connect()` refuses unbonded
  stoves instead of starting a bond in the background.

## Testing workflow

- **TDD** for anything in `protocol/` or `data/`: write the failing test first
  (`app/src/test/kotlin`, `FakeTransport` for the repository).
- UI: Wear OS emulator + debug build, pair the **Demo stove** (emulators have no Bluetooth).
  Grab screens with `adb exec-out screencap -p > file.png`. Skill: `emulator-check`.
- Real device: release build over wireless ADB. Skill: `install-watch`. Useful logs:
  `adb logcat -s StoveBle BluetoothGatt bt_btm_sec`.

## Known stove behaviour (verified on hardware)

- The Maestro module drops links from unbonded devices about 70 ms after connecting.
- Bonding only succeeds while the panel's Bluetooth icon blinks (+ and − pressed together).
- MTU 517 is accepted; status block reads (51/77/20 registers) need it.

## Google Play

- Listing text/graphics: `fastlane/metadata/android/<locale>/` (en-US, fr-FR). Keep the
  title free of trademarks; mention MCZ/Maestro only as compatibility, always "unofficial".
- Upload key comes from `STOVE_UPLOAD_*` Gradle properties or env vars; never commit keys.
- Bump `versionCode` and add `changelogs/<versionCode>.txt` for every upload.

## Repo hygiene

- Public repo (Apache-2.0). Don't commit local paths, personal data, keystores, or
  `local.properties`. Internal planning notes stay out of git.
- Conventional commit subjects (`feat:`, `fix:`, `docs:`, `ci:`, `build:`); update
  `CHANGELOG.md` under *Unreleased* for user-visible changes.
