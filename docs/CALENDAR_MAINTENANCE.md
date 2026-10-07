# Updating the offline calendar

## Inputs and output

- `content/config.json`: schema version, content version, supported years, source
  links and editorial status.
- `content/fixed.tsv`: independently authored short commemorations. Columns are
  `stable-id|MM-DD|Romanian name|English name|red 1/0|semicolon-separated aliases`.
  Keep the ID stable when editing names. Add a new ID for a genuinely new observance.
- `content/overrides.json`: authoritative corrections for individual ISO dates.
  A correction replaces any supplied field; `additionalObservances` appends to
  the day's names. `_note` records why the correction exists and is not shipped.
- `scripts/build_calendar.py`: generates movable commemorations and baseline
  fasting rules, then applies overrides. It has no network access.
- `app/src/main/assets/calendar.json`: generated, committed daily records read
  directly by the app. There is no runtime calendar calculation or server fetch.

Records include `date`, `observances`, `fasting`, `period`, `periodStart`, and
`sunday`. Observances contain `id`, bilingual `name`, `red` and `aliases`.

Fasting values are `NONE`, `FAST_FREE`, `FAST`, `OIL_WINE`, `FISH`, and `DAIRY`.
`DAIRY` means meat abstinence in Cheesefare Week and is not a standalone fasting
reminder. A fish allowance still counts as fasting. Period IDs are `lent`,
`apostles`, `dormition`, and `nativity`.

## Annual update

1. Add the next year to `years`, retaining all existing years. Increase
   `contentVersion` and set `reviewStatus` to `pending` while editing.
2. Update the fixed list for new commemorations, preserving IDs. Complete the
   daily roster rather than assuming the current short selection is exhaustive.
3. Generate proposals with `python scripts/build_calendar.py`.
4. Check every day against the approved Romanian Orthodox Church calendar for
   that year. Record exceptions in `overrides.json`. Check saints transferred
   because of Holy Week/Pascha, feast rank, fish/oil/wine allowances, and new saints.
5. Check Romanian spelling and independently review English names and aliases.
   Language selection must not change the observance tradition.
6. Confirm source provenance and rights for any additional text. The repository
   contains original concise names; it does not bundle scraped biographies,
   prayers, icons, images, website markup or proprietary reference documents.
7. Once the whole bundle has been reviewed, record the review in a dated file
   under `content/reviews/`, set `reviewStatus` to `reviewed`, regenerate, then run:

```powershell
python scripts/build_calendar.py --check --release
python scripts/test_calendar.py
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

8. Increase Android `versionCode` and `versionName` in `app/build.gradle.kts`.
   Install the new APK over the previous version with `adb install -r` to check
   that preferences, selected language and reminders remain intact. Build/sign
   and publish the update using your normal Google Play process.

The generator deliberately supports years 1900–2099 only. Supporting more years
requires a reviewed computus change. Leap years are structurally supported, but
February 28/29 commemoration rules need explicit annual editorial review.

## Reference work completed

References consulted on 2026-10-06:

- https://calendar.patriarhia.ro/ — 2026 daily reference. Fifty-three factual
  corrections record explicit allowances and the transferred April 11/12
  commemorations on Bright Monday, April 13.
- https://calendar.patriarhia.ro/zile-de-post-si-post-uri-de-peste-an/ — verified
  2026 fasting-period boundaries: February 23–April 11, June 8–28, July 31–August
  14, and November 15–December 24.
- https://edituradoxologia.ro/sites/default/files/pdf/2018/11/pages_from_agenda_2019_interior_428_p_a5.pdf
  — reference for the 2027 Paschal date, May 2.

These checks do not constitute complete content review. The main outstanding
work is the exhaustive daily roster, rank/allowance reconciliation and review of
the 2027 annual calendar. The seven-day minimum Apostles' Fast proposal for 2027
starts June 22; specifically verify this against the approved annual calendar.

## Example correction

```json
{
  "2027-11-23": {
    "fasting": "FISH",
    "_note": "EXAMPLE ONLY: replace with an actually verified source and decision."
  }
}
```

Do not copy that example into production without verification. To move a saint,
replace `observances` on the original date and append the same stable observance
ID at its actual date. Search then finds the actual upcoming date automatically.

Outside bundled coverage the app shows an unavailable message, search explicitly
reports exhausted coverage, and notifications have no events to send. An app
update is the only way to extend coverage.
