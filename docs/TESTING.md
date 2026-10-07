# Device verification

Automated checks are listed in README. Before release, also verify on a physical
device; emulator results do not establish behavior under manufacturer battery
restrictions.

1. Install fresh, switch to airplane mode, browse both years and search `Craciun`,
   `Înălțarea`, `Ascension`, and `Parascheva`. Open a result and return to Today.
2. Switch Romanian/English, kill and reopen the app. Confirm all UI, daily names,
   notifications and widget text use the chosen language. Test after installing
   from an AAB, not only an APK.
3. Enable/deny notification permission. Check the blocked state and Android
   notification-settings link. Disable each reminder category independently.
4. Set a time a few minutes ahead on an eligible day. Expect one combined
   notification; tap it to open tomorrow. Test a non-eligible day and a day inside
   a longer fast. Ordinary alarms may be delayed by Android.
5. Reboot with reminders enabled. Change time zone, cross a DST transition and
   cross midnight. Verify no duplicate or stale "tomorrow" notification.
6. Add the widget to a launcher, resize it, change language/theme, and cross a
   date boundary. Tap it. Confirm all text is readable and matches Today.
7. Set system font size to maximum, use TalkBack, and test light/dark themes.
   Check the day grid, settings controls and long translated saint names.
8. Install a newer version over the existing signed app. Confirm theme, language,
   reminder switches and time are preserved; calendar corrections must appear
   without clearing data, including in the widget.
9. Test a date beyond bundled coverage: no invented saint list, no calendar
   navigation outside available years, and an explicit search coverage message.

Publication also requires the content review described in
`CALENDAR_MAINTENANCE.md`. No physical device was attached during implementation.
