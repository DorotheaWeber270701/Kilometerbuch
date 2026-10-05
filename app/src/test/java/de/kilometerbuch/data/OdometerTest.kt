package de.kilometerbuch.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

class OdometerTest {

    private val sep = YearMonth.of(2026, 9)
    private val car = Car("a", "Golf", 0, odometerKm = 40_000, odometerMonth = YearMonth.of(2026, 6))
    private val entries = listOf(
        Entry("a", YearMonth.of(2026, 7), 1_000, null),
        Entry("a", YearMonth.of(2026, 8), 1_000, null),
        Entry("a", sep, 1_000, null),
        Entry("b", sep, 5_000, null),
    )

    @Test
    fun odometerIsCarriedForwardWithMonthlyKm() {
        // Juni zählt nicht mit (da abgelesen), Juli bis September schon; das andere Auto nicht.
        assertEquals(43_000, estimateOdometer(car, entries))
    }

    @Test
    fun higherReadingIsAddedToChosenMonth() {
        val (newCar, newEntries) = correctOdometer(car, entries, actual = 43_250, month = sep)
        assertEquals(1_250, newEntries.single { it.carId == "a" && it.month == sep }.km)
        assertEquals(43_250, estimateOdometer(newCar, newEntries))
    }

    @Test
    fun lowerReadingIsSubtractedButNeverBelowZero() {
        val (_, smaller) = correctOdometer(car, entries, actual = 42_600, month = sep)
        assertEquals(600, smaller.single { it.carId == "a" && it.month == sep }.km)
        val (_, clamped) = correctOdometer(car, entries, actual = 41_000, month = sep)
        assertEquals(0, clamped.single { it.carId == "a" && it.month == sep }.km)
    }

    @Test
    fun missingMonthIsCreatedForPositiveDifference() {
        val oct = YearMonth.of(2026, 10)
        val (newCar, newEntries) = correctOdometer(car, entries, actual = 43_400, month = oct)
        assertEquals(400, newEntries.single { it.carId == "a" && it.month == oct }.km)
        assertEquals(43_400, estimateOdometer(newCar, newEntries))
    }

    @Test
    fun firstReadingJustSetsTheOdometer() {
        val fresh = car.copy(odometerKm = null, odometerMonth = null)
        val (newCar, newEntries) = correctOdometer(fresh, entries, actual = 50_000, month = sep)
        assertEquals(entries, newEntries)
        assertEquals(50_000, estimateOdometer(newCar, newEntries))
    }
}
