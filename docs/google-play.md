# Publishing on Google Play

Store listing text and graphics live in [`fastlane/metadata/android`](../fastlane/metadata/android)
(fastlane `supply` layout, also usable by hand):

| Play Console field | File | Limit |
|---|---|---|
| App name | `<locale>/title.txt` | 30 chars |
| Short description | `<locale>/short_description.txt` | 80 chars |
| Full description | `<locale>/full_description.txt` | 4000 chars |
| Release notes | `<locale>/changelogs/<versionCode>.txt` | 500 chars |
| App icon | `en-US/images/icon.png` | 512 × 512 |
| Feature graphic | `en-US/images/featureGraphic.png` | 1024 × 500 |
| Wear OS screenshots | `en-US/images/wearScreenshots/*.png` | 1:1, ≥ 384 px |

Locales: `en-US` (default) and `fr-FR`. Graphics fall back to `en-US`.

## 1. Upload key (once)

Use Play App Signing: Google keeps the app signing key; you sign uploads with an upload key.

```bash
keytool -genkeypair -v -keystore ~/keys/stove-remote-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then in `~/.gradle/gradle.properties` (never in this repo):

```properties
STOVE_UPLOAD_STORE_FILE=/absolute/path/to/stove-remote-upload.jks
STOVE_UPLOAD_STORE_PASSWORD=...
STOVE_UPLOAD_KEY_ALIAS=upload
STOVE_UPLOAD_KEY_PASSWORD=...
```

The same names also work as environment variables (CI). Back the keystore up: losing it means
asking Google support for an upload key reset.

## 2. Build

```bash
./gradlew :app:testDebugUnitTest :app:bundleRelease
# -> app/build/outputs/bundle/release/app-release.aab
```

Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every upload, add
`changelogs/<versionCode>.txt`, and update `CHANGELOG.md`.

## 3. Play Console

- **Create app:** name *Stove Remote for Wear OS*, app, free.
- **Form factor:** add *Wear OS*; the app is standalone (no phone app). Opt in to Wear OS
  distribution and accept the Wear OS review requirements (tiles, round screens, dark UI).
- **Category:** House & Home (or Tools). Tags: smart home, remote control.
- **Privacy policy URL:** https://github.com/nosari20/MCZ-WearOS/blob/main/PRIVACY.md
- **Data safety:** no data collected, no data shared. Data is not encrypted in transit
  because none is transmitted off the device. No account creation; data deletion: uninstall.
- **Ads:** no ads.
- **Content rating (IARC questionnaire):** utility app, no user-generated content, no
  communication, no location sharing → expected rating *Everyone / PEGI 3*.
- **Target audience:** 18+ (controls a heating appliance); not designed for children.
- **App access:** testing needs a compatible stove. In *App access*, explain that the app
  controls MCZ Maestro stoves over Bluetooth, that reviewers without a stove will see the
  pairing screen, and link the README screenshots.
- **Permissions:** `BLUETOOTH_SCAN` is declared `neverForLocation`, so no location
  permission and no background location declaration are needed.
- **Trademarks:** the listing uses "MCZ" and "Maestro" only to describe compatibility and
  states that the app is unofficial. Don't use MCZ logos in graphics.

## 4. Release tracks

1. Upload the `.aab` to **Internal testing**, add your Google account as tester, install on
   the watch from the Play Store and run the real-stove checklist (pairing, setpoint, mode,
   power, fan, on/off, tile).
2. Promote to **Closed testing**. New personal developer accounts must run a closed test
   with at least 12 testers for 14 days before production access.
3. Promote to **Production**.
