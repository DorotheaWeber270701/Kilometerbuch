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
    private val maintenance = listOf(
        MaintenanceCost("m1", "a", LocalDate.of(2026, 3, 12), MaintenanceCategory.INSPECTION, 289.9, "Ölwechsel; Filter"),
        MaintenanceCost("m2", "b", LocalDate.of(2026, 5, 2), MaintenanceCategory.TIRES, 64.0),
    )

    @Test
    fun exportThenImportGivesSameData() {
        val parsed = Csv.parse(Csv.write(cars, entries, receipts, maintenance))

        assertEquals(emptyList<Int>(), parsed.badLines)
        assertEquals(
            listOf(
                Csv.Trip("Firma; Kombi", YearMonth.of(2026, 9), 980, null),
                Csv.Trip("Golf", YearMonth.of(2026, 8), 1250, 6.4),
            ),
            parsed.trips,
        )
        assertEquals(listOf(Csv.Fuel("Golf", LocalDate.of(2026, 9, 16), 40.9, 72.36)), parsed.fuel)
        assertEquals(
            listOf(
                Csv.Maintenance("Firma; Kombi", LocalDate.of(2026, 5, 2), MaintenanceCategory.TIRES, 64.0, null),
                Csv.Maintenance("Golf", LocalDate.of(2026, 3, 12), MaintenanceCategory.INSPECTION, 289.9, "Ölwechsel; Filter"),
            ),
            parsed.maintenance,
        )
    }

    @Test
    fun exportUsesGermanNumbersAndComputedPrice() {
        val text = Csv.write(cars, entries, receipts, maintenance)
        assertTrue(text, text.contains("Tanken;Golf;;;;2026-09-16;40,9;72,36;1,769"))
        assertTrue(text, text.contains("Wartung;Golf;;;;2026-03-12;;289,90;;Inspektion;\"Ölwechsel; Filter\""))
        assertTrue(text, text.contains("\"Firma; Kombi\""))
    }

    @Test
    fun acceptsOlderFileWithoutMaintenanceColumns() {
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

    @Test
    fun unknownCategoryBecomesOther() {
        val text = "Typ;Auto;Datum;Kosten (€);Art\nWartung;Golf;2026-01-05;99,50;Waschanlage\n"
        assertEquals(MaintenanceCategory.OTHER, Csv.parse(text).maintenance.single().category)
    }

    @Test
    fun rejectsFileWithoutRequiredColumns() {
        val e = runCatching { Csv.parse("Datum;Betrag\n2026-09-01;50\n") }.exceptionOrNull() as Csv.FormatException
        assertEquals(Csv.Problem.MISSING_COLUMNS, e.problem)
    }

    @Test
    fun rejectsEmptyFile() {
        val e = runCatching { Csv.parse("\n\n") }.exceptionOrNull() as Csv.FormatException
        assertEquals(Csv.Problem.EMPTY, e.problem)
    }
}
