package de.kilometerbuch.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Erlaubte Wertebereiche, gleich für Eingabe und CSV-Import. */
val KM_RANGE = 0..100_000
val L100_RANGE = 0.5..50.0
val LITERS_RANGE = 0.5..300.0
val TOTAL_RANGE = 0.5..2000.0

fun parseKm(text: String): Int? = text.trim().replace(".", "").replace(" ", "").toIntOrNull()

/** Kommazahl wie „6,4“ oder „6.4“; bei „1.234,56“ gilt der Punkt als Tausendertrenner. */
fun parseDecimal(text: String): Double? {
    val t = text.trim().replace(" ", "")
    val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normalized.toDoubleOrNull()
}

/** „2026-09“, „09.2026“ oder „9/2026“. */
fun parseMonth(text: String): YearMonth? {
    val t = text.trim()
    runCatching { return YearMonth.parse(t) }
    val m = Regex("""(\d{1,2})[./](\d{4})""").matchEntire(t) ?: return null
    return runCatching { YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
}

private val GERMAN_DATE = DateTimeFormatter.ofPattern("d.M.yyyy")

/** „2026-09-16“ oder „16.09.2026“. */
fun parseDate(text: String): LocalDate? {
    val t = text.trim()
    return runCatching { LocalDate.parse(t) }.getOrNull()
        ?: runCatching { LocalDate.parse(t, GERMAN_DATE) }.getOrNull()
}
