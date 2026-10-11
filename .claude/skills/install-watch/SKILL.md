---
name: install-watch
description: Build the release APK and install it on a real Wear OS watch over wireless ADB, then check Bluetooth logs. Use when asked to deploy, install or push the app to the watch, or to debug the app against a real stove.
---

# Install on a real watch

1. **Find the watch.** Wireless debugging must be on (watch: Settings → Developer options →
   Wireless debugging).
   ```bash
   adb mdns services          # look for _adb-tls-connect entries
   adb devices -l             # phones and watches may both be listed: check the model
   adb -s <serial> shell getprop ro.build.characteristics   # must contain "watch"
   ```
   If the watch is listed by mDNS but `adb connect <ip:port>` fails with no pairing, ask the
   user for the pairing IP:port and 6-digit code shown under *Pair new device*, then
   `adb pair <ip:pairing-port> <code>` and `adb connect <ip:debug-port>`. Watches drop Wi-Fi
   when idle: retry the connect a few times before giving up.

2. **Build and install the release build** (debug builds scroll poorly on watches):
   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleRelease
   adb -s <watch> install -r app/build/outputs/apk/release/app-release.apk
   ```

3. **Logs while the user tests** (stream in the background, read on request):
   ```bash
   adb -s <watch> logcat -c
   adb -s <watch> logcat -v time StoveBle:V BluetoothGatt:V bt_btm_sec:V bt_bta_dm_sec:V '*:S'
   ```
   How to read them:
   - `Bond result 12`: bonded. `Bond result 10`: pairing failed, so the stove panel was not
     in pairing mode.
   - `connected=true`, then `connected=false status=257` with "Clearing security flags for
     unbonded device": the stove rejected an unbonded link.
   - `MTU 517 (status 0)` + `setCharacteristicNotification … abf2`: link ready.
   - `Dropped notification of N bytes`: frame failed decrypt/CRC; check the MTU.

4. Screenshot to see what the user sees: `adb -s <watch> exec-out screencap -p > watch.png`.

Never write stove registers from adb or a script; all commands go through the app's UI.
