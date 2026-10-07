# Calendar Ortodox

A native Android calendar in Romanian and English. Kotlin, Jetpack Compose,
Material 3 and Glance. Android 8.0 (API 26) or newer.

The application is self-contained: calendar records are bundled in the APK/AAB,
preferences and reminders stay on the device, and the manifest does not request
internet access. Both languages are included even when installed from an App
Bundle. There are no accounts, advertising, analytics, backend or remote APIs.

## Included

- Today, tomorrow preview, saints, feast markings and fasting allowances.
- Monday-first month/week calendar with month/year selection and day details.
- Romanian/English search with diacritic-insensitive names and aliases. Exact
  names/aliases rank first, then the next occurrence including today.
- Optional combined evening reminders: red-marked days (including Sundays),
  standalone fast days and the beginning of longer fasts.
- Resizable home-screen widget, theme selection, large text and accessibility labels.
- 730 dated records for 2026–2027; no extrapolation outside bundled coverage.

## Content status — read before publishing

**The application is functional, but the bundled calendar is an editorial preview,
not a publication-ready church calendar.** It contains an independently written
selection of daily commemorations, original English short translations, calculated
movable dates, and 53 corrections checked against the Romanian Patriarchate's
2026 calendar. It is not yet a complete list of all daily commemorations.

The complete saint list, red markings, fasting allowances (especially 2027),
translations and source/redistribution review remain to be signed off before
publication. The app identifies this preview status. No claim of ecclesiastical
approval is made. Automated coverage tests establish structural completeness,
not theological or editorial accuracy.

See [calendar maintenance](docs/CALENDAR_MAINTENANCE.md) for the data format,
corrections and review workflow. Do not mark `reviewStatus` as `reviewed` simply
because tests pass.

## Build and run

1. Open this directory in Android Studio. Use JDK 17 or newer (tested with the
   installed Android Studio JBR), Android SDK Platform 36, and Build Tools 35+.
2. Let Android Studio create `local.properties`, or set `ANDROID_HOME` to the SDK.
3. Run on an emulator or connected Android device.

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The initial Gradle dependency download needs internet access on the development
computer. The installed application does not.

## Verification

```powershell
python scripts/build_calendar.py --check
python scripts/test_calendar.py
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The last command needs an emulator or device. Tests cover calendar coverage,
Pascha, fasting boundaries, bilingual search, notification decisions, DST,
navigation, language persistence, local settings and manifest permissions.

## Release handoff

Publishing, final application ID/branding, Play Console setup and release signing
remain yours. The initial application ID is `ro.calendarortodox.app`; choose the
final ID before first publication and keep it unchanged for all later updates.
Do not distribute the debug signing key as your release identity.

After calendar review:

```powershell
python scripts/build_calendar.py --check --release
.\gradlew.bat :app:bundleRelease
```

Use Android Studio's **Generate Signed Bundle / APK** to create a signed release
bundle with your own keystore. Signing secrets are not included in this project.
Increase `versionCode` for each Play update. The app reads the new bundled content,
retains preferences and refreshes its reminder schedule and widgets on replacement.

See [manual device checks](docs/TESTING.md) and [architecture](docs/ARCHITECTURE.md).
