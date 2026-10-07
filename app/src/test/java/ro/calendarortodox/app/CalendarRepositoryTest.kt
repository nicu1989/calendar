package ro.calendarortodox.app

import org.junit.Assert.*
import org.junit.Test
import ro.calendarortodox.app.data.*
import java.time.*

class CalendarRepositoryTest {
    private val repo = CalendarRepository.parse(javaClass.classLoader!!.getResource("calendar.json")!!.readText())
    private fun day(value: String) = repo.day(LocalDate.parse(value))!!

    @Test fun `bundle covers both years without gaps or untranslated entries`() {
        assertEquals(730, repo.bundle.days.size)
        var date = LocalDate.of(2026, 1, 1)
        while (date <= LocalDate.of(2027, 12, 31)) {
            val day = repo.day(date)!!
            assertTrue(day.observances.isNotEmpty())
            assertTrue(day.observances.all { it.name.ro.isNotBlank() && it.name.en.isNotBlank() })
            date = date.plusDays(1)
        }
        assertNull(repo.day(LocalDate.of(2028, 1, 1)))
    }

    @Test fun `search ignores accents punctuation and case across both languages`() {
        val today = LocalDate.of(2026, 10, 6)
        val ro = repo.search("INALTAREA domnului", today).first()
        val en = repo.search("Ascension", today).first()
        assertEquals(en.observance.id, ro.observance.id)
        assertEquals(LocalDate.of(2027, 6, 10), ro.nextDate)
        assertEquals(LocalDate.of(2026, 12, 25), repo.search("Craciun", today).first().nextDate)
    }

    @Test fun `search includes today and retains matches beyond coverage`() {
        assertEquals(LocalDate.of(2026, 12, 25), repo.search("Christmas", LocalDate.of(2026, 12, 25)).first().nextDate)
        assertEquals(LocalDate.of(2027, 12, 25), repo.search("Christmas", LocalDate.of(2026, 12, 26)).first().nextDate)
        assertNull(repo.search("Christmas", LocalDate.of(2027, 12, 26)).first().nextDate)
        assertTrue(repo.search("   ", LocalDate.now()).isEmpty())
        assertTrue(repo.search("not-a-real-feast", LocalDate.now()).isEmpty())
    }

    @Test fun `Orthodox Easter and movable feasts use the correct civil dates`() {
        assertTrue(day("2026-04-12").observances.any { it.id == "pascha" })
        assertTrue(day("2027-05-02").observances.any { it.id == "pascha" })
        assertTrue(day("2026-05-31").observances.any { it.id == "pentecost" })
        assertTrue(day("2027-06-20").observances.any { it.id == "pentecost" })
    }

    @Test fun `fasting exceptions and period boundaries are preserved`() {
        assertTrue(day("2026-07-31").periodStart)
        assertFalse(day("2026-08-01").periodStart)
        assertTrue(day("2027-06-22").periodStart) // Romanian minimum seven-day Apostles' fast.
        assertEquals(Fast.FISH, day("2026-03-25").fasting)
        assertEquals(Fast.FISH, day("2026-04-05").fasting)
        assertEquals(Fast.FAST_FREE, day("2026-04-15").fasting)
        assertEquals(Fast.FAST, day("2026-09-14").fasting)
        assertEquals(Fast.DAIRY, day("2026-02-18").fasting)
        assertEquals(Fast.FAST_FREE, day("2026-12-25").fasting)
    }

    @Test fun `reminders honor categories and suppress interior days of longer fasts`() {
        assertTrue(day("2026-02-23").shouldRemind(false, true))
        assertFalse(day("2026-02-24").shouldRemind(false, true))
        assertTrue(day("2026-03-25").shouldRemind(true, false))
        assertFalse(day("2026-03-25").shouldRemind(false, true))
        assertTrue(day("2026-10-07").shouldRemind(false, true))
        assertFalse(day("2026-04-15").shouldRemind(false, true))
        assertFalse(day("2026-09-14").shouldRemind(false, false))
        assertTrue(day("2026-09-14").shouldRemind(true, true))
    }

    @Test fun `local alarm calculation follows DST and year rollover`() {
        val zone = ZoneId.of("Europe/Bucharest")
        val now = ZonedDateTime.of(2026, 3, 28, 19, 0, 0, 0, zone)
        val next = ReminderPolicy.nextTime(now, LocalTime.of(18, 0))
        assertEquals(18, next.hour)
        assertEquals(ZoneOffset.ofHours(3), next.offset)
        assertEquals(23, Duration.between(now.withHour(18), next).toHours())
        val yearEnd = ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2027, 1, 1), ReminderPolicy.nextTime(yearEnd, LocalTime.of(18, 0)).toLocalDate())
    }

    @Test fun `deferred alarms do not send a reminder for the wrong tomorrow`() {
        assertNull(ReminderPolicy.targetIfFresh("2026-10-06", LocalDate.of(2026, 10, 7)))
        assertEquals(LocalDate.of(2026, 10, 7), ReminderPolicy.targetIfFresh("2026-10-06", LocalDate.of(2026, 10, 6)))
    }
}
