# Privacy Policy — Stove Remote for Wear OS

*Last updated: 2026-10-11*

Stove Remote ("the app") is an open-source Wear OS app that controls a compatible pellet stove
over Bluetooth Low Energy. This policy explains what the app does with data.

## Data collected

**None.** The app does not collect, transmit, sell or share any personal or usage data.
It contains no analytics, advertising, crash reporting or tracking SDKs, and it does not
use the internet.

## Data stored on the watch

The app stores only on your watch, in its private storage:

- the Bluetooth address and name of the stove you paired;
- the last values read from the stove (temperatures, state, settings) and when they were read,
  so the tile can show them instantly.

This data never leaves the watch. Uninstalling the app, or clearing its storage, deletes it.

## Permissions

| Permission | Why |
|---|---|
| Nearby devices: Bluetooth scan (`BLUETOOTH_SCAN`) | Find your stove when you pair it. Declared with `neverForLocation`: the app does not derive your location from Bluetooth scans. |
| Nearby devices: Bluetooth connect (`BLUETOOTH_CONNECT`) | Connect to the stove to read its state and send the commands you confirm. |

## Children

The app is not directed at children and collects no data from anyone.

## Changes

Changes to this policy are published in this file in the project repository:
https://github.com/nosari20/MCZ-WearOS/blob/main/PRIVACY.md

## Contact

Questions: open an issue at https://github.com/nosari20/MCZ-WearOS/issues
