"""Build deterministic, offline daily records. No downloads or third-party packages.

The generator proposes dates; editorial overrides have final authority. See
docs/CALENDAR_MAINTENANCE.md before marking a bundle reviewed.
"""
from __future__ import annotations
import argparse
import calendar
import json
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/calendar.json"


def pascha(year: int) -> date:
    """Julian computus converted to the Gregorian civil date (1900–2099)."""
    if not 1900 <= year <= 2099:
        raise ValueError("Computus supports 1900–2099; extend and review before adding other years")
    a, b, c = year % 4, year % 7, year % 19
    d = (19 * c + 15) % 30
    e = (2 * a + 4 * b - d + 34) % 7
    n = d + e + 114
    return date(year, n // 31, n % 31 + 1) + timedelta(days=13)


def obs(key: str, ro: str, en: str, red: bool = False, aliases=None) -> dict:
    return {"id": key, "name": {"ro": ro, "en": en}, "red": red, "aliases": aliases or []}


def fixed_calendar() -> dict:
    fixed: dict[str, list] = {}
    ids = set()
    for line in (ROOT / "content/fixed.tsv").read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        key, md, ro, en, red, aliases = line.split("|")
        assert key not in ids, f"Duplicate fixed ID: {key}"
        ids.add(key)
        date.fromisoformat(f"2024-{md}")  # Validate month/day, allowing February 29.
        assert red in ("0", "1"), f"Invalid red flag for {key}"
        fixed.setdefault(md, []).append(obs(key, ro, en, red == "1", [a for a in aliases.split(";") if a]))
    return fixed


def movable_calendar(easter: date) -> dict:
    entries = [
        (-70, "publican", "Duminica Vameșului și a Fariseului", "Sunday of the Publican and Pharisee", True, []),
        (-63, "prodigal", "Duminica Fiului Risipitor", "Sunday of the Prodigal Son", True, []),
        (-56, "judgment", "Duminica Înfricoșătoarei Judecăți", "Sunday of the Last Judgment", True, []),
        (-49, "forgiveness", "Duminica Iertării", "Forgiveness Sunday", True, ["Lasatul secului de branza"]),
        (-42, "orthodoxy", "Duminica Ortodoxiei", "Sunday of Orthodoxy", True, []),
        (-35, "palamas", "Duminica Sfântului Grigorie Palama", "Sunday of Saint Gregory Palamas", True, []),
        (-28, "cross", "Duminica Sfintei Cruci", "Sunday of the Holy Cross", True, []),
        (-21, "climacus", "Duminica Sfântului Ioan Scărarul", "Sunday of Saint John Climacus", True, []),
        (-14, "mary-egypt", "Duminica Sfintei Maria Egipteanca", "Sunday of Saint Mary of Egypt", True, []),
        (-8, "lazarus", "Sâmbăta lui Lazăr", "Lazarus Saturday", False, []),
        (-7, "palm", "Intrarea Domnului în Ierusalim", "Entry of the Lord into Jerusalem", True, ["Florii", "Palm Sunday"]),
        (-3, "holy-thursday", "Sfânta și Marea Joi", "Holy Thursday", False, []),
        (-2, "good-friday", "Sfânta și Marea Vineri", "Holy Friday", False, ["Vinerea Mare", "Good Friday"]),
        (-1, "holy-saturday", "Sfânta și Marea Sâmbătă", "Holy Saturday", False, []),
        (0, "pascha", "Învierea Domnului", "Resurrection of the Lord", True, ["Paste", "Pasti", "Easter", "Pascha"]),
        (1, "bright-monday", "A doua zi de Paști", "Bright Monday", True, ["Easter Monday"]),
        (2, "bright-tuesday", "A treia zi de Paști", "Bright Tuesday", True, ["Easter Tuesday"]),
        (5, "life-giving-spring", "Izvorul Tămăduirii", "Life-Giving Spring", True, []),
        (7, "thomas-sunday", "Duminica Sfântului Apostol Toma", "Thomas Sunday", True, []),
        (14, "myrrhbearers", "Duminica Mironosițelor", "Sunday of the Myrrh-Bearing Women", True, []),
        (14, "romanian-women", "Soborul Sfintelor Femei Românce", "Synaxis of the Holy Romanian Women", True, []),
        (39, "ascension", "Înălțarea Domnului", "Ascension of the Lord", True, ["Ispas", "Ziua Eroilor"]),
        (49, "pentecost", "Pogorârea Sfântului Duh", "Descent of the Holy Spirit", True, ["Rusalii", "Cincizecime", "Pentecost"]),
        (50, "trinity", "Sfânta Treime", "Holy Trinity", True, []),
        (56, "all-saints", "Duminica Tuturor Sfinților", "All Saints Sunday", True, []),
        (63, "romanian-saints", "Duminica Sfinților Români", "Sunday of the Romanian Saints", True, []),
    ]
    result: dict[date, list] = {}
    for offset, key, ro, en, red, aliases in entries:
        result.setdefault(easter + timedelta(days=offset), []).append(obs(key, ro, en, red, aliases))
    return result


def fasting(day: date, easter: date) -> tuple[str, str | None, bool]:
    y, md, wd = day.year, day.strftime("%m-%d"), day.weekday()
    offset = (day - easter).days
    dormition_start = date(y, 8, 1)
    if date(y, 7, 31).weekday() in (2, 4):
        dormition_start = date(y, 7, 31)
    nativity_start = date(y, 11, 15)
    if date(y, 11, 14).weekday() in (2, 4):
        nativity_start = date(y, 11, 14)
    apostles_start = min(easter + timedelta(days=57), date(y, 6, 22))
    periods = [
        ("lent", easter - timedelta(days=48), easter - timedelta(days=1)),
        ("apostles", apostles_start, date(y, 6, 28)),
        ("dormition", dormition_start, date(y, 8, 14)),
        ("nativity", nativity_start, date(y, 12, 24)),
    ]
    for period, start, end in periods:
        if start <= day <= end:
            status = "FAST"
            if wd >= 5 and offset != -1:
                status = "OIL_WINE"
            if period == "lent":
                if offset == -7 or (md == "03-25" and offset < -6):
                    status = "FISH"
                elif md in ("02-24", "03-09", "03-24", "03-26") or offset in (-17, -3):
                    status = "OIL_WINE"
            elif period == "dormition" and md == "08-06":
                status = "FISH"
            elif period == "apostles":
                if wd >= 5 or md == "06-24":
                    status = "FISH"
                elif wd in (1, 3):
                    status = "OIL_WINE"
            elif period == "nativity":
                if wd in (1, 3):
                    status = "OIL_WINE"
                if (wd >= 5 and "11-21" <= md <= "12-19") or md in ("11-21", "11-30", "12-06"):
                    status = "FISH"
                if md == "12-24":
                    status = "FAST"
            return status, period, day == start
    if md in ("01-05", "08-29", "09-14"):
        return "FAST", None, False
    if "12-25" <= md or md <= "01-04" or md == "01-06" or -70 <= offset <= -64 or 0 <= offset <= 6 or 49 <= offset <= 55:
        return "FAST_FREE", None, False
    if -55 <= offset <= -49:
        return "DAIRY", None, False
    if wd in (2, 4):
        if 7 <= offset <= 38 or md in ("01-07", "02-02", "06-29", "08-15", "09-08"):
            return "FISH", None, False
        return "FAST", None, False
    return "NONE", None, False


def build() -> dict:
    config = json.loads((ROOT / "content/config.json").read_text(encoding="utf-8"))
    overrides = json.loads((ROOT / "content/overrides.json").read_text(encoding="utf-8"))
    allowed_fields = {"observances", "additionalObservances", "fasting", "period", "periodStart", "_note"}
    for key, correction in overrides.items():
        assert date.fromisoformat(key).year in config["years"], f"Override outside bundled years: {key}"
        assert set(correction) <= allowed_fields, f"Unknown override field for {key}"
    fixed = fixed_calendar()
    days = []
    for year in config["years"]:
        easter = pascha(year)
        movable = movable_calendar(easter)
        day = date(year, 1, 1)
        while day.year == year:
            status, period, start = fasting(day, easter)
            day_observances = movable.get(day, []) + fixed[day.strftime("%m-%d")]
            record = {"date": day.isoformat(), "observances": day_observances,
                      "fasting": status, "period": period, "periodStart": start, "sunday": day.weekday() == 6}
            override = dict(overrides.get(day.isoformat(), {}))
            override.pop("_note", None)
            record["observances"] = record["observances"] + override.pop("additionalObservances", [])
            record.update(override)
            record["observances"] = sorted(record["observances"], key=lambda o: not o["red"])
            days.append(record)
            day += timedelta(days=1)
    return {**config, "days": days}


def validate(bundle: dict, release: bool = False) -> None:
    assert bundle["schemaVersion"] == 1
    assert len(bundle["years"]) == len(set(bundle["years"]))
    assert sorted(bundle["years"]) == list(range(min(bundle["years"]), max(bundle["years"]) + 1)), "Coverage must be consecutive"
    expected = {date(y, m, d).isoformat() for y in bundle["years"] for m in range(1, 13) for d in range(1, calendar.monthrange(y, m)[1] + 1)}
    actual = [d["date"] for d in bundle["days"]]
    assert len(actual) == len(set(actual)), "Duplicate dates"
    assert set(actual) == expected, "Missing or unexpected dates"
    names = {}
    allowed = {"NONE", "FAST_FREE", "FAST", "OIL_WINE", "FISH", "DAIRY"}
    for record in bundle["days"]:
        assert record["fasting"] in allowed, record["date"]
        assert record["observances"], record["date"]
        assert len({o["id"] for o in record["observances"]}) == len(record["observances"]), record["date"]
        assert record["sunday"] == (date.fromisoformat(record["date"]).weekday() == 6)
        assert not record["periodStart"] or record["period"], record["date"]
        for o in record["observances"]:
            assert o["name"]["ro"].strip() and o["name"]["en"].strip(), o["id"]
            if o["id"] in names:
                assert names[o["id"]] == o["name"], f"Inconsistent name for {o['id']}"
            names[o["id"]] = o["name"]
    if release:
        assert bundle["reviewStatus"] == "reviewed", "Calendar content has not passed editorial review. See docs/CALENDAR_MAINTENANCE.md."


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Verify committed bundle matches source; do not write")
    parser.add_argument("--release", action="store_true", help="Require complete editorial review")
    args = parser.parse_args()
    bundle = build()
    validate(bundle, args.release)
    serialized = json.dumps(bundle, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        assert OUTPUT.read_text(encoding="utf-8") == serialized, "Bundle is stale; run python scripts/build_calendar.py"
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(serialized, encoding="utf-8")
    print(f"Validated {len(bundle['days'])} days, {bundle['years']}; editorial status: {bundle['reviewStatus']}")


if __name__ == "__main__":
    main()
