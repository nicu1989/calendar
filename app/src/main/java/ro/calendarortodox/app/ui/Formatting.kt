package ro.calendarortodox.app.ui

import android.content.Context
import ro.calendarortodox.app.R
import ro.calendarortodox.app.data.Fast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dateLabel(date: LocalDate, language: String, pattern: String = "EEEE, d MMMM yyyy"): String =
    date.format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(language)))

fun Fast.labelId(): Int = when (this) {
    Fast.NONE -> R.string.fast_none
    Fast.FAST_FREE -> R.string.fast_free
    Fast.FAST -> R.string.fast_strict
    Fast.OIL_WINE -> R.string.fast_oil
    Fast.FISH -> R.string.fast_fish
    Fast.DAIRY -> R.string.fast_dairy
}

fun periodLabel(period: String): Int = when (period) {
    "lent" -> R.string.period_lent
    "apostles" -> R.string.period_apostles
    "dormition" -> R.string.period_dormition
    else -> R.string.period_nativity
}
