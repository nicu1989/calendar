# Architecture

One Android application module, no backend and no runtime networking.

- **CalendarRepository** decodes immutable daily records from the packaged JSON
  asset. It provides date lookup and normalized bilingual search. The application
  owns a lazy singleton; the first UI load uses an IO dispatcher.
- **Compose UI** has Today and Calendar destinations plus Settings. A single
  day-details component presents selected dates and notification destinations.
- **Preferences** uses Preferences DataStore for settings and delivery
  deduplication. AppCompat manages per-app locales. A local locale preference
  also makes the selected language available to widgets on pre-Android-13
  devices before any Activity has opened. Android 13+ uses system app locales.
- **ReminderScheduler** uses one local inexact AlarmManager alarm for the next
  evening. The receiver reads the installed bundle and current preferences,
  combines the applicable categories, records a per-target delivery claim and
  schedules the next alarm. Alarm metadata is private SharedPreferences; no
  personal data is stored. A mutex protects rescheduling within the process.
- **RefreshReceiver** rebuilds the schedule and widget on reboot, replacement,
  date/time/time-zone and locale changes. WorkManager provides a periodic recovery
  refresh. It does not send extra or catch-up notifications.
- **CalendarWidget** uses Glance and the same repository, current language and
  theme. Widget refreshes use Android's 30-minute update period, platform events,
  app foregrounding and the recovery worker. Exact midnight refresh is not
  guaranteed; the displayed date makes a stale widget identifiable.

Both language resource sets are included in every AAB installation
(`bundle.language.enableSplit=false`). Backup/device transfer is excluded to
match the local-only privacy statement. Clearing app data removes preferences.

No database migration is needed for calendar updates: the next APK replaces the
asset, and a new process reads its content version. DataStore's stable preference
keys preserve user settings. Future incompatible JSON schemas must update both
the reader and generator; the current reader rejects unknown schemas.

## Notification limits

The reminder time is approximate. Android battery policy and force-stop can
prevent or delay delivery. A reminder delayed past midnight is discarded so its
"tomorrow" wording never refers to the wrong date. Reopening the app recovers
scheduling after force-stop. Delivery is deduplicated by target date; turning a
switch off/on after a reminder has arrived does not resend it that evening.

Sundays qualify as red-marked days even when no named fixed feast is red. Fasting
reminders occur on standalone fasting days and at period starts, not repeatedly
inside a longer fast. Feast reminders continue inside those periods.
