# Contributing

Thanks for helping. Bug reports, stove compatibility reports and pull requests are welcome.

## Reporting a bug or a new stove model

Open an issue using the templates. For Bluetooth problems, include:

- Watch model and Wear OS version.
- Stove model and Maestro module/panel version if known.
- Logs: `adb logcat -s StoveBle StoveApp` while reproducing the problem.

Never post your stove's serial number or other personal data in public issues.

## Development

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

- Kotlin, official code style (`kotlin.code.style=official`), 4-space indent.
- Protocol and repository logic must stay Android-free in `protocol/` and `data/` so it can
  be unit-tested on the JVM.
- **Test first.** Every behaviour change in `protocol/` or `data/` needs a unit test that
  fails before the change.
- Anything that writes to the stove must keep the safety rules from the README: confirm in the
  UI, re-read state before toggling, never retry writes.
- The emulator has no Bluetooth: use the debug-only **Demo stove** to check UI changes, and
  test Bluetooth changes on a real watch.

## Pull requests

1. Fork, create a branch from `main`.
2. Keep the change focused; describe what you tested (unit tests, emulator, real stove).
3. Make sure CI is green.

By contributing you agree that your contributions are licensed under the
[Apache License 2.0](LICENSE).
