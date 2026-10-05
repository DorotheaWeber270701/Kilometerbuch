package de.kilometerbuch.data

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Eine CSV-Datei für alles: Spalte „Typ“ unterscheidet Fahrten, Tankbelege und Wartung.
 * Semikolon und Dezimalkomma, damit ein deutsches Excel sie direkt öffnet. Das Format ist bewusst
 * unabhängig von der App-Sprache, damit Sicherungen überall wieder eingelesen werden können.
 */
object Csv {

    private const val SEP = ';'
    private const val NEWLINE = "\r\n"

    private const val TYPE_TRIP = "Fahrt"
    private const val TYPE_FUEL = "Tanken"
    private const val TYPE_MAINTENANCE = "Wartung"

    private val HEADER = listOf(
        "Typ", "Auto", "Monat", "Kilometer", "Verbrauch (l/100 km)",
        "Datum", "Liter", "Kosten (€)", "Preis (€/l)", "Art", "Notiz",
    )

    data class Trip(val car: String, val month: YearMonth, val km: Int, val l100: Double?)
    data class Fuel(val car: String, val date: LocalDate, val liters: Double, val total: Double)
    data class Maintenance(
        val car: String,
        val date: LocalDate,
        val category: MaintenanceCategory,
        val amount: Double,
        val note: String?,
    )
    data class Parsed(
        val trips: List<Trip>,
        val fuel: List<Fuel>,
        val maintenance: List<Maintenance>,
        val badLines: List<Int>,
    )

    enum class Problem { EMPTY, MISSING_COLUMNS }

    /** Die Datei lässt sich gar nicht verwenden; der Text dazu kommt aus den Sprachdateien. */
    class FormatException(val problem: Problem) : Exception(problem.name)

    fun write(
        cars: List<Car>,
        entries: List<Entry>,
        receipts: List<FuelReceipt>,
        maintenance: List<MaintenanceCost>,
    ): String {
        val names = cars.associate { it.id to it.name }
        val sb = StringBuilder()
        fun row(vararg fields: String) {
            sb.append(fields.joinToString(SEP.toString(), transform = ::escape)).append(NEWLINE)
        }
        row(*HEADER.toTypedArray())
        entries.sortedWith(compareBy({ names[it.carId] }, { it.month })).forEach { e ->
            row(TYPE_TRIP, names[e.carId].orEmpty(), e.month.toString(), e.km.toString(), e.l100?.let(::plain).orEmpty(), "", "", "", "", "", "")
        }
        receipts.sortedWith(compareBy({ names[it.carId] }, { it.date })).forEach { r ->
            row(
                TYPE_FUEL, names[r.carId].orEmpty(), "", "", "",
                r.date.toString(), plain(r.liters), money(r.total),
                String.format(Locale.GERMANY, "%.3f", r.pricePerLiter), "", "",
            )
        }
        maintenance.sortedWith(compareBy({ names[it.carId] }, { it.date })).forEach { m ->
            row(
                TYPE_MAINTENANCE, names[m.carId].orEmpty(), "", "", "",
                m.date.toString(), "", money(m.amount), "", m.category.code, m.note.orEmpty(),
            )
        }
        return sb.toString()
    }

    fun parse(text: String): Parsed {
        val lines = text.removePrefix("﻿").lines()
        val headerIndex = lines.indexOfFirst { it.isNotBlank() }
        if (headerIndex < 0) throw FormatException(Problem.EMPTY)

        val headerLine = lines[headerIndex]
        val sep = if (headerLine.count { it == ';' } >= headerLine.count { it == ',' }) ';' else ','
        val header = split(headerLine, sep).map { it.trim().lowercase() }
        fun col(prefix: String) = header.indexOfFirst { it.startsWith(prefix) }

        val cType = col("typ")
        val cCar = col("auto")
        if (cType < 0 || cCar < 0) throw FormatException(Problem.MISSING_COLUMNS)
        val cMonth = col("monat")
        val cKm = col("kilometer")
        val cL100 = col("verbrauch")
        val cDate = col("datum")
        val cLiters = col("liter")
        val cTotal = col("kosten")
        val cCategory = col("art")
        val cNote = col("notiz")

        val trips = mutableListOf<Trip>()
        val fuel = mutableListOf<Fuel>()
        val maintenance = mutableListOf<Maintenance>()
        val bad = mutableListOf<Int>()

        for (i in headerIndex + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val f = split(line, sep)
            fun get(c: Int) = if (c < 0) "" else f.getOrNull(c)?.trim().orEmpty()
            val lineNo = i + 1
            val car = get(cCar)

            when (get(cType).lowercase()) {
                "fahrt", "fahrten" -> {
                    val month = parseMonth(get(cMonth))
                    val km = parseKm(get(cKm))
                    val l100Text = get(cL100)
                    val l100 = if (l100Text.isBlank()) null else parseDecimal(l100Text)
                    val ok = month != null && km != null && km in KM_RANGE &&
                        (l100Text.isBlank() || (l100 != null && l100 in L100_RANGE))
                    if (ok) trips += Trip(car, month!!, km!!, l100) else bad += lineNo
                }

                "tanken", "tankbeleg" -> {
                    val date = parseDate(get(cDate))
                    val liters = parseDecimal(get(cLiters))
                    val total = parseDecimal(get(cTotal))
                    val ok = date != null && liters != null && liters in LITERS_RANGE && total != null && total in TOTAL_RANGE
                    if (ok) fuel += Fuel(car, date!!, liters!!, total!!) else bad += lineNo
                }

                "wartung" -> {
                    val date = parseDate(get(cDate))
                    val amount = parseDecimal(get(cTotal))
                    val category = MaintenanceCategory.fromCode(get(cCategory)) ?: MaintenanceCategory.OTHER
                    val note = get(cNote).ifBlank { null }
                    val ok = date != null && amount != null && amount in AMOUNT_RANGE
                    if (ok) maintenance += Maintenance(car, date!!, category, amount!!, note) else bad += lineNo
                }

                else -> bad += lineNo
            }
        }
        return Parsed(trips, fuel, maintenance, bad)
    }

    /** 6.4 → „6,4“, ohne unnötige Nullen. */
    private fun plain(v: Double) = BigDecimal.valueOf(v).stripTrailingZeros().toPlainString().replace('.', ',')

    private fun money(v: Double) = String.format(Locale.GERMANY, "%.2f", v)

    private fun escape(field: String): String =
        if (field.any { it == SEP || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /** Teilt eine Zeile; Felder in Anführungszeichen dürfen das Trennzeichen enthalten. */
    private fun split(line: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == sep && !quoted -> {
                    out += cur.toString()
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
