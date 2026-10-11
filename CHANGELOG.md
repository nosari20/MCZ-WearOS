# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-11

### Added
- Standalone Wear OS app controlling an MCZ Maestro stove over BLE (no phone, no cloud).
- Room temperature, setpoint, stove state, mode, power level, fan level and active alarm.
- On/off behind a confirmation screen; only toggles from a stable state, 60 s cooldown.
- Setpoint (0.5 °C steps), mode, power level and fan control.
- Tile with status, refresh on view and power shortcut.
- Pairing screen with explicit bonding (stove panel must be in pairing mode).
- Debug-only demo stove for emulator testing.
- Verified on a Pixel Watch 3 with a Maestro stove.

[Unreleased]: https://github.com/nosari20/MCZ-WearOS/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/nosari20/MCZ-WearOS/releases/tag/v1.0.0
