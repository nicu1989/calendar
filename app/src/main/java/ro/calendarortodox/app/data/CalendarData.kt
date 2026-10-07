package ro.calendarortodox.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.Locale

@Serializable
data class LocalizedText(val ro: String, val en: String) {
    fun inLanguage(language: String) = if (language == "ro") ro else en
}

@Serializable
data class Observance(val id: String, val name: LocalizedText, val red: Boolean = false, val aliases: List<String> = emptyList())

@Serializable
enum class Fast { NONE, FAST_FREE, FAST, OIL_WINE, FISH, DAIRY }

@Serializable
data class CalendarDay(
    val date: String,
    val observances: List<Observance>,
    val fasting: Fast = Fast.NONE,
    val period: String? = null,
    val periodStart: Boolean = false,
    val sunday: Boolean = false,
) {
    val localDate get() = LocalDate.parse(date)
    val isFast get() = fasting in setOf(Fast.FAST, Fast.OIL_WINE, Fast.FISH)
    val red get() = sunday || observances.any { it.red }
    fun shouldRemind(feasts: Boolean, fasts: Boolean) =
        (feasts && red) || (fasts && isFast && (period == null || periodStart))
}

@Serializable
data class CalendarBundle(
    val schemaVersion: Int,
    val contentVersion: String,
    val reviewStatus: String,
    val years: List<Int>,
    val sources: List<String>,
    val days: List<CalendarDay>,
)

data class SearchMatch(val observance: Observance, val nextDate: LocalDate?)

class CalendarRepository(val bundle: CalendarBundle) {
    private val byDate = bundle.days.associateBy { it.localDate }
    val firstDate: LocalDate = byDate.keys.min()
    val lastDate: LocalDate = byDate.keys.max()
    val years = bundle.years.sorted()
    fun day(date: LocalDate) = byDate[date]
    fun nearest(date: LocalDate) = date.coerceIn(firstDate, lastDate)

    fun search(query: String, from: LocalDate): List<SearchMatch> {
        val tokens = normalize(query).split(" ").filter(String::isNotBlank)
        if (tokens.isEmpty()) return emptyList()
        val grouped = bundle.days.flatMap { d -> d.observances.map { it to d.localDate } }.groupBy { it.first.id }
        return grouped.values.mapNotNull { occurrences ->
            val candidate = occurrences.first().first
            val searchable = normalize(listOf(candidate.name.ro, candidate.name.en).plus(candidate.aliases).joinToString(" "))
            if (tokens.all { it in searchable }) SearchMatch(candidate, occurrences.map { it.second }.filter { it >= from }.minOrNull()) else null
        }.sortedWith(compareBy<SearchMatch> {
            val names = listOf(it.observance.name.ro, it.observance.name.en) + it.observance.aliases
            if (names.any { name -> normalize(name) == normalize(query) }) 0 else 1
        }.thenBy { it.nextDate ?: LocalDate.MAX }.thenBy { it.observance.id })
    }

    companion object {
        fun parse(text: String): CalendarRepository {
            val bundle = Json.decodeFromString<CalendarBundle>(text)
            require(bundle.schemaVersion == 1) { "Unsupported calendar schema" }
            require(bundle.days.isNotEmpty()) { "Calendar is empty" }
            require(bundle.days.map { it.date }.distinct().size == bundle.days.size) { "Duplicate date" }
            require(bundle.days.all { it.observances.isNotEmpty() }) { "Missing observances" }
            return CalendarRepository(bundle)
        }
        fun normalize(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    }
}

object ReminderPolicy {
    fun nextTime(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today > now) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
    // Do not deliver a yesterday reminder if Android deferred the alarm beyond midnight.
    fun targetIfFresh(scheduledDate: String?, now: LocalDate): LocalDate? =
        if (scheduledDate == now.toString()) now.plusDays(1) else null
}
