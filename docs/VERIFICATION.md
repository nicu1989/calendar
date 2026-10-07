# Implementation verification — 2026-10-06

## Passed

- Debug APK build and minified release Android App Bundle build.
- Android lint: **0 errors**. Remaining warnings concern dependency updates,
  newer optional XML attributes, KTX style suggestions and unused resources.
- **8 JVM tests**: complete date coverage, bilingual search, exact aliases,
  upcoming dates, Orthodox Pascha, fasting boundaries, notification categories,
  daylight-saving transitions and stale-alarm suppression.
- **6 Python tests**: computus reference dates, 2026 fasting boundaries, bundle
  validation and rejection of missing data/translations or unreviewed release content.
- **8 Android instrumentation tests** on an Android 16 / API 36 emulator:
  navigation/search, week/month/year controls, language persistence, settings
  persistence, manifest permissions, notification deduplication, actual local
  notification delivery/deep link and compact/large Glance rendering.
- The same 8 Android tests passed with airplane mode enabled.
- APK replacement using `adb install -r` preserved the DataStore preferences
  byte-for-byte and the Android per-app locale.
- Visually inspected Today, the Romanian dark calendar, large text and rendered
  compact/large widget layouts. Fixed header/search clipping found during inspection.
- Confirmed the release AAB contains `base/assets/calendar.json` and has no
  signing blocks. Release signing belongs to the publisher.

## Artifacts

- `artifacts/calendar-ortodox-debug.apk`: installable debug build for testing.
- `artifacts/calendar-ortodox-preview-unsigned.aab`: unsigned release build,
  still containing preview calendar content; not ready for publication.
- `artifacts/today-ro-dark.png`, `calendar-ro-dark.png`,
  `calendar-large-text.png`, `widget-compact.png`, `widget-large.png`: local captures.

## Remaining release work

The calendar remains **editorially pending**. It contains 730 dated records and
a selection of saints, not the exhaustive daily roster. The complete roster,
feast ranks, all fasting allowances, English translations and source permissions
need a complete review. The 2027 baseline particularly needs comparison with the
approved annual calendar. See `CALENDAR_MAINTENANCE.md`.

No physical device was available. Actual scheduled-alarm delivery after reboot,
midnight widget refresh and manufacturer-specific battery restrictions still
need device checks. The notification test exercises the real Android delivery
path with a controlled date; it does not wait for a physical evening alarm.

Publication, branding/application ID decisions, signing and Play Console setup
remain with the publisher.
