package de.kilometerbuch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CsvTest {

    private val cars = listOf(Car("a", "Golf", 0), Car("b", "Firma; Kombi", 1))
    private val entries = listOf(
        Entry("a", YearMonth.of(2026, 8), 1250, 6.4),
        Entry("b", YearMonth.of(2026, 9), 980, null),
    )
    private val receipts = listOf(
        FuelReceipt("r1", "a", LocalDate.of(2026, 9, 16), 40.9, 72.36),
    )

    @Test
    fun exportThenImportGivesSameData() {
        val parsed = Csv.parse(Csv.write(cars, entries, receipts))

        assertEquals(emptyList<Int>(), parsed.badLines)
        assertEquals(
            listOf(
                Csv.Trip("Firma; Kombi", YearMonth.of(2026, 9), 980, null),
                Csv.Trip("Golf", YearMonth.of(2026, 8), 1250, 6.4),
            ),
            parsed.trips,
        )
        assertEquals(listOf(Csv.Fuel("Golf", LocalDate.of(2026, 9, 16), 40.9, 72.36)), parsed.fuel)
    }

    @Test
    fun exportUsesGermanNumbersAndComputedPrice() {
        val text = Csv.write(cars, entries, receipts)
        assertTrue(text, text.contains("Tanken;Golf;;;;2026-09-16;40,9;72,36;1,769"))
        assertTrue(text, text.contains("\"Firma; Kombi\""))
    }

    @Test
    fun acceptsCommaSeparatedFileWithGermanDates() {
        val text = "Typ,Auto,Monat,Kilometer,Verbrauch,Datum,Liter,Kosten\n" +
            "Fahrt,Golf,09.2026,1300,6.1,,,\n" +
            "Tanken,Golf,,,,16.09.2026,40.5,70.20\n" +
            "Tanken,Golf,,,,kein Datum,40,70\n"
        val parsed = Csv.parse(text)
        assertEquals(1, parsed.trips.size)
        assertEquals(YearMonth.of(2026, 9), parsed.trips[0].month)
        assertEquals(1, parsed.fuel.size)
        assertEquals(listOf(4), parsed.badLines)
    }

    @Test(expected = Csv.FormatException::class)
    fun rejectsFileWithoutRequiredColumns() {
        Csv.parse("Datum;Betrag\n2026-09-01;50\n")
    }
}
