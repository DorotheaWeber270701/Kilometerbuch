package de.kilometerbuch.ui

import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.FuelReceipt
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// --- Fahrten ---

data class YearStats(
    val total: Int,
    val months: Int,
    val avgKm: Double?,
    /** Nach Kilometern gewichtet, nur Monate mit Verbrauchsangabe. */
    val avgL100: Double?,
    val liters: Double?,
)

fun yearStats(entries: List<Entry>, year: Int): YearStats {
    val inYear = entries.filter { it.month.year == year }
    val total = inYear.sumOf { it.km }
    val months = inYear.map { it.month }.distinct().size
    val withL = inYear.filter { it.l100 != null }
    val kmWithL = withL.sumOf { it.km }
    return YearStats(
        total = total,
        months = months,
        avgKm = if (months == 0) null else total.toDouble() / months,
        avgL100 = if (kmWithL > 0) withL.sumOf { it.km * it.l100!! } / kmWithL else null,
        liters = if (withL.isEmpty()) null else withL.sumOf { it.km * it.l100!! / 100.0 },
    )
}

fun suggestMonth(entries: List<Entry>): YearMonth {
    val now = YearMonth.now()
    val prev = now.minusMonths(1)
    return if (entries.none { it.month == prev }) prev else now
}

// --- Tanken ---

/** Summe aller Belege eines Monats. */
data class MonthFuel(val liters: Double, val euros: Double) {
    val pricePerLiter: Double get() = euros / liters
}

fun fuelByMonth(receipts: List<FuelReceipt>): Map<YearMonth, MonthFuel> =
    receipts.groupBy { YearMonth.from(it.date) }
        .mapValues { (_, rs) -> MonthFuel(rs.sumOf { it.liters }, rs.sumOf { it.total }) }

data class FuelYearStats(
    val euros: Double,
    val liters: Double,
    val receipts: Int,
    /** Durchschnitt über die Monate, in denen getankt wurde. */
    val avgPerMonth: Double?,
    /** Gesamtkosten durch Gesamtliter. */
    val avgPrice: Double?,
)

fun fuelYearStats(receipts: List<FuelReceipt>, year: Int): FuelYearStats {
    val inYear = receipts.filter { it.date.year == year }
    val euros = inYear.sumOf { it.total }
    val liters = inYear.sumOf { it.liters }
    val months = inYear.map { YearMonth.from(it.date) }.distinct().size
    return FuelYearStats(
        euros = euros,
        liters = liters,
        receipts = inYear.size,
        avgPerMonth = if (months == 0) null else euros / months,
        avgPrice = if (liters > 0) euros / liters else null,
    )
}

// --- Zeitraum für Diagramme ---

enum class ChartRange(val label: String, val months: Int?) {
    M12("12 Monate", 12),
    M24("24 Monate", 24),
    ALL("Alles", null),
}

/** Lückenlose Monatsfolge bis zum letzten Monat mit Daten. */
fun monthRange(months: Collection<YearMonth>, range: ChartRange): List<YearMonth> {
    if (months.isEmpty()) return emptyList()
    val first = months.min()
    val end = months.max()
    val start = range.months?.let { maxOf(first, end.minusMonths(it - 1L)) } ?: first
    return generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
}

// --- Formatierung und Eingabe ---

private val GERMAN = Locale.GERMANY
private val MONTHS_SHORT = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
private val MONTHS_LONG = listOf(
    "Januar", "Februar", "März", "April", "Mai", "Juni",
    "Juli", "August", "September", "Oktober", "November", "Dezember",
)
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun fmtInt(v: Double): String = NumberFormat.getIntegerInstance(GERMAN).format(v.roundToLong())
fun fmt1(v: Double): String = String.format(GERMAN, "%.1f", v)
fun fmt2(v: Double): String = String.format(GERMAN, "%,.2f", v)
fun fmt3(v: Double): String = String.format(GERMAN, "%.3f", v)
fun fmtDate(d: LocalDate): String = d.format(DATE_FORMAT)
fun monthLong(m: YearMonth) = "${MONTHS_LONG[m.monthValue - 1]} ${m.year}"
fun monthShort(m: YearMonth) = MONTHS_SHORT[m.monthValue - 1]

/** Text für ein Eingabefeld, z. B. 6.4 → „6,4“. */
fun decimalInput(v: Double): String = v.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')
