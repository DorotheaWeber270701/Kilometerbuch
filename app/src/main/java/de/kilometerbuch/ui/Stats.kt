package de.kilometerbuch.ui

import androidx.annotation.StringRes
import de.kilometerbuch.R
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.FuelReceipt
import de.kilometerbuch.data.MaintenanceCost
import de.kilometerbuch.i18n.L10n
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

enum class ChartRange(@StringRes val label: Int, val months: Int?) {
    M12(R.string.range_12, 12),
    M24(R.string.range_24, 24),
    ALL(R.string.range_all, null),
}

/** Lückenlose Monatsfolge bis zum letzten Monat mit Daten. */
fun monthRange(months: Collection<YearMonth>, range: ChartRange): List<YearMonth> {
    if (months.isEmpty()) return emptyList()
    val first = months.min()
    val end = months.max()
    val start = range.months?.let { maxOf(first, end.minusMonths(it - 1L)) } ?: first
    return generateSequence(start) { it.plusMonths(1) }.takeWhile { !it.isAfter(end) }.toList()
}

// --- Wartung ---

/** Summe der Wartungskosten in [year]. */
fun maintenanceSum(costs: List<MaintenanceCost>, year: Int): Double =
    costs.filter { it.date.year == year }.sumOf { it.amount }

// --- Formatierung in der App-Sprache ---

fun fmtInt(v: Double): String = NumberFormat.getIntegerInstance(L10n.locale).format(v.roundToLong())
fun fmt1(v: Double): String = String.format(L10n.locale, "%.1f", v)
fun fmt2(v: Double): String = String.format(L10n.locale, "%,.2f", v)
fun fmt3(v: Double): String = String.format(L10n.locale, "%.3f", v)
fun fmtDate(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(L10n.locale))

/** Datum mit Wochentag, z. B. „Mi., 14.10.2026“. */
fun fmtWeekdayDate(d: LocalDate): String = "${d.format(DateTimeFormatter.ofPattern("EEE", L10n.locale))}, ${fmtDate(d)}"

/** Ausgeschriebenes Datum für Hinweise, z. B. „Mittwoch, 14. Oktober 2026“. */
fun fmtLongDate(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(L10n.locale))

fun monthLong(m: YearMonth) = "${L10n.monthName(m.monthValue)} ${m.year}"
fun monthShort(m: YearMonth) = L10n.monthShortName(m.monthValue)

/** Text für ein Eingabefeld, z. B. 6.4 → „6,4“ (mit dem Dezimalzeichen der Sprache). */
fun decimalInput(v: Double): String =
    v.toBigDecimal().stripTrailingZeros().toPlainString().replace('.', DecimalFormatSymbols.getInstance(L10n.locale).decimalSeparator)
