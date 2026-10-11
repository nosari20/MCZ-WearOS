---
name: emulator-check
description: Verify UI or tile changes on a Wear OS emulator using the debug build and the simulated Demo stove. Use after changing anything in ui/ or tile/, or when asked to run or screenshot the app.
---

# Check the app on a Wear OS emulator

Emulators have no Bluetooth: use the debug build and pair the **Demo stove**
(`DemoStoveTransport`, shown on the pairing screen in debug builds only).

1. **Pick or start a Wear OS AVD.** Several emulators may already run: identify by
   `adb -s <serial> emu avd name` and `getprop ro.build.characteristics` (must contain
   `watch`). Start one in the background if needed:
   `$ANDROID_SDK/emulator/emulator -avd <wear-avd> -no-snapshot-save -no-boot-anim`
   and wait until `adb -s <serial> shell getprop sys.boot_completed` returns `1`.

2. **Install and launch:**
   ```bash
   ./gradlew :app:assembleDebug
   adb -s <emu> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s <emu> shell pm grant com.mczadvanced.wear android.permission.BLUETOOTH_SCAN
   adb -s <emu> shell pm grant com.mczadvanced.wear android.permission.BLUETOOTH_CONNECT
   adb -s <emu> shell am start -n com.mczadvanced.wear/.ui.MainActivity
   ```

3. **Drive and look.** Take a screenshot after each step and read it:
   - `adb -s <emu> exec-out screencap -p > step.png`
   - `adb -s <emu> shell input tap X Y`, `input swipe 227 380 227 120 300` to scroll.

   Flow to cover: pairing screen → *Demo stove* → main shows *Off*, 20.5°, *Set 21.5°* →
   *Turn on* → confirm → main shows *On* → *+* twice → *Set 22.5°* → Mode picker.

4. **Tile:**
   ```bash
   adb -s <emu> shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation add-tile --ecn component com.mczadvanced.wear/.tile.StoveTileService
   adb -s <emu> shell am broadcast -a com.google.android.wearable.app.DEBUG_SYSUI --es operation show-tile --ei index 0
   ```
   Expect *Stove · <state>*, room temperature, setpoint, age, and an edge button that opens
   the confirm screen.

5. For R8 changes, also install `assembleRelease` and check main screen + tile for crashes
   (`adb logcat -d | grep -E "FATAL|AndroidRuntime"`).

The demo stove state is kept in memory: reinstalling resets it to *Off*.
