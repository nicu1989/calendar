import copy
import unittest
from datetime import date
from build_calendar import build, validate, pascha, fasting


class CalendarTests(unittest.TestCase):
    def test_known_paschal_dates(self):
        for year, expected in [(2024, "2024-05-05"), (2025, "2025-04-20"), (2026, "2026-04-12"), (2027, "2027-05-02"), (2028, "2028-04-16")]:
            self.assertEqual(expected, pascha(year).isoformat())

    def test_2026_patriarchate_fasting_boundaries(self):
        for start, end in [("2026-02-23", "2026-04-11"), ("2026-06-08", "2026-06-28"), ("2026-07-31", "2026-08-14"), ("2026-11-15", "2026-12-24")]:
            self.assertTrue(fasting(date.fromisoformat(start), pascha(2026))[2])
            self.assertIsNotNone(fasting(date.fromisoformat(end), pascha(2026))[1])

    def test_generated_bundle_is_complete(self):
        validate(build())

    def test_validator_rejects_missing_day(self):
        bundle = build()
        bundle["days"].pop()
        with self.assertRaises(AssertionError):
            validate(bundle)

    def test_validator_rejects_missing_translation(self):
        bundle = copy.deepcopy(build())
        bundle["days"][0]["observances"][0]["name"]["en"] = ""
        with self.assertRaises(AssertionError):
            validate(bundle)

    def test_preview_content_cannot_pass_release_validation(self):
        bundle = build()
        bundle["reviewStatus"] = "pending"
        with self.assertRaisesRegex(AssertionError, "editorial review"):
            validate(bundle, release=True)


if __name__ == "__main__":
    unittest.main()
