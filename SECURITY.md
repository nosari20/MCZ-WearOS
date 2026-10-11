# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Use GitHub's
[private vulnerability reporting](https://github.com/nosari20/MCZ-WearOS/security/advisories/new)
instead. You should get an answer within a few days.

Relevant examples: a way to send commands to the stove without user confirmation, a way to
make the power toggle fire more than once, or a crash triggered by crafted Bluetooth data.

## Scope note

The AES key, IV and token in `FrameCodec.kt` are fixed constants of the MCZ Maestro module
firmware, already public through earlier reverse-engineering. They are not secrets of this
project, and the protection of the stove's Bluetooth link is MCZ's responsibility. Bluetooth
access to the stove is limited by its pairing mode (it has to be enabled on the stove panel).

## Supported versions

Only the latest release on `main` gets fixes.
