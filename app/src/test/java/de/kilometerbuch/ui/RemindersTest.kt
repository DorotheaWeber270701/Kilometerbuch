package de.kilometerbuch.ui

import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.estimateOdometer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RemindersTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun monthly(km: Int, from: YearMonth, count: Int) =
        (0 until count).map { Entry("a", from.plusMonths(it.toLong()), km, null) }

    // --- HU ---

    @Test
    fun huInPlaketteMonthIsSoon() {
        val r = huReminder(CarCare("a", huDue = YearMonth.of(2026, 10)), today)!!
        assertEquals(Urgency.SOON, r.urgency)
        assertEquals(LocalDate.of(2026, 10, 31), r.due)
    }

    @Test
    fun huNextMonthIsAlreadySoon() {
        assertEquals(Urgency.SOON, huReminder(CarCare("a", huDue = YearMonth.of(2026, 11)), today)!!.urgency)
    }

    @Test
    fun huInHalfAYearIsLater() {
        assertEquals(Urgency.LATER, huReminder(CarCare("a", huDue = YearMonth.of(2027, 4)), today)!!.urgency)
    }

    @Test
    fun huLastMonthIsOverdue() {
        val r = huReminder(CarCare("a", huDue = YearMonth.of(2026, 9)), today)!!
        assertEquals(Urgency.OVERDUE, r.urgency)
        assertTrue(r.detail, r.detail.startsWith("Seit 1 Monat überfällig"))
    }

    @Test
    fun huWarnTimeIsConfigurable() {
        // Plakette Dezember: mit 1 Monat Vorlauf noch „später“, mit 2 Monaten schon „bald“.
        val month = YearMonth.of(2026, 12)
        assertEquals(Urgency.LATER, huReminder(CarCare("a", huDue = month, huWarnDays = 30), today)!!.urgency)
        assertEquals(Urgency.SOON, huReminder(CarCare("a", huDue = month, huWarnDays = 60), today)!!.urgency)
    }

    @Test
    fun bookedAppointmentReplacesPlaketteMonth() {
        val care = CarCare("a", huDue = YearMonth.of(2026, 10), huAppointment = LocalDate.of(2026, 10, 14))
        val r = huReminder(care, today)!!
        assertEquals(LocalDate.of(2026, 10, 14), r.due)
        assertEquals(LocalDate.of(2026, 10, 14), r.appointment)
        assertEquals(Urgency.LATER, r.urgency)
        assertTrue(r.headline, r.headline.startsWith("Termin am Mi"))
    }

    @Test
    fun appointmentWithinAWeekIsSoon() {
        val care = CarCare("a", huDue = YearMonth.of(2026, 10), huAppointment = LocalDate.of(2026, 10, 3))
        val r = huReminder(care, today)!!
        assertEquals(Urgency.SOON, r.urgency)
        assertTrue(r.detail, r.detail.startsWith("Morgen"))
    }

    @Test
    fun nextHuIsTwoYearsAfterInspection() {
        assertEquals(YearMonth.of(2028, 10), nextHuAfter(YearMonth.of(2026, 10)))
    }

    // --- Inspektion ---

    @Test
    fun serviceDueByTimeWhenDrivingLittle() {
        // 500 km/Monat: 15.000 km wären erst nach 30 Monaten erreicht, also gilt das Jahr.
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), serviceMonths = 12, serviceKm = 15_000)
        val r = serviceReminder(care, monthly(500, YearMonth.of(2026, 4), 6), today)!!
        assertEquals(LocalDate.of(2027, 3, 31), r.due)
        assertEquals(Urgency.LATER, r.urgency)
        assertTrue(r.detail, r.detail.contains("3.000 von 15.000 km"))
    }

    @Test
    fun serviceDueByKmWhenDrivingALot() {
        // 2.000 km/Monat seit April: 12.000 km gefahren, 3.000 übrig → in 2 Monaten.
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), serviceMonths = 12, serviceKm = 15_000)
        val r = serviceReminder(care, monthly(2_000, YearMonth.of(2026, 4), 6), today)!!
        assertEquals(LocalDate.of(2026, 12, 31), r.due)
        assertTrue(r.headline, r.headline.startsWith("Voraussichtlich"))
    }

    @Test
    fun serviceSoonWhenLessThanOneMonthOfKmLeft() {
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), serviceMonths = 12, serviceKm = 13_000)
        val r = serviceReminder(care, monthly(2_000, YearMonth.of(2026, 4), 6), today)!!
        assertEquals(Urgency.SOON, r.urgency)
    }

    @Test
    fun serviceOverdueWhenKmExceeded() {
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), serviceMonths = 24, serviceKm = 10_000)
        val r = serviceReminder(care, monthly(2_000, YearMonth.of(2026, 4), 6), today)!!
        assertEquals(Urgency.OVERDUE, r.urgency)
        assertEquals("Kilometergrenze erreicht", r.headline)
    }

    @Test
    fun serviceMonthItselfDoesNotCount() {
        // Der Inspektionsmonat zählt nicht mit, erst die Monate danach.
        val care = CarCare("a", lastService = YearMonth.of(2026, 4), serviceKm = 15_000)
        val r = serviceReminder(care, monthly(1_000, YearMonth.of(2026, 4), 3), today)!!
        assertTrue(r.detail, r.detail.contains("2.000 von 15.000 km"))
    }

    @Test
    fun noServiceReminderWithoutLastService() {
        assertNull(serviceReminder(CarCare("a"), emptyList(), today))
    }

    // --- Reifen ---

    @Test
    fun winterTiresSoonInOctober() {
        val r = tireReminder(CarCare("a"), today)!!
        assertEquals(ReminderKind.WINTER_TIRES, r.kind)
        assertEquals(Urgency.SOON, r.urgency)
        assertEquals("2026-winter", r.seasonKey)
    }

    @Test
    fun afterWinterDoneNextIsSummer() {
        val r = tireReminder(CarCare("a", tiresDone = "2026-winter"), today)!!
        assertEquals(ReminderKind.SUMMER_TIRES, r.kind)
        assertEquals(Urgency.LATER, r.urgency)
        assertEquals(LocalDate.of(2027, 4, 30), r.due)
    }

    @Test
    fun winterTiresOverdueInNovember() {
        assertEquals(Urgency.OVERDUE, tireReminder(CarCare("a"), LocalDate.of(2026, 11, 10))!!.urgency)
    }

    @Test
    fun inSummerNextIsWinterLater() {
        val r = tireReminder(CarCare("a"), LocalDate.of(2026, 7, 1))!!
        assertEquals(ReminderKind.WINTER_TIRES, r.kind)
        assertEquals(Urgency.LATER, r.urgency)
    }

    // --- Schalter ---

    @Test
    fun onlySwitchedOnItemsProduceReminders() {
        val car = Car("a", "Golf", 0)
        val care = CarCare("a", huOn = false, huDue = YearMonth.of(2026, 10), tiresOn = true)
        val kinds = reminders(car, care, emptyList(), today).map { it.kind }
        assertEquals(listOf(ReminderKind.WINTER_TIRES), kinds)
    }

    @Test
    fun switchingOffKeepsTheData() {
        val care = CarCare("a", huOn = true, huDue = YearMonth.of(2027, 5)).withOn(CareItem.HU, false)
        assertEquals(YearMonth.of(2027, 5), care.huDue)
        assertTrue(!care.anyOn)
    }

    // --- Kilometerstand ---

    @Test
    fun odometerIsCarriedForwardWithMonthlyKm() {
        val car = Car("a", "Golf", 0, odometerKm = 40_000, odometerMonth = YearMonth.of(2026, 6))
        // Juni zählt nicht mit (da abgelesen), Juli bis September schon.
        assertEquals(43_000, estimateOdometer(car, monthly(1_000, YearMonth.of(2026, 6), 4)))
    }

    @Test
    fun serviceUsesOdometerWhenServiceKmKnown() {
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), lastServiceOdometer = 40_000, serviceKm = 15_000)
        val r = serviceReminder(care, monthly(1_000, YearMonth.of(2026, 4), 6), today, odometer = 52_000)!!
        assertTrue(r.detail, r.detail.contains("12.000 von 15.000 km"))
        assertTrue(r.detail, r.detail.contains("bei 40.000 km"))
    }

    // --- Bremsflüssigkeit ---

    @Test
    fun brakeFluidDueTwoYearsAfterLastChange() {
        val r = brakeFluidReminder(CarCare("a", lastBrakeFluid = YearMonth.of(2024, 10)), today)!!
        assertEquals(LocalDate.of(2026, 10, 31), r.due)
        assertEquals(Urgency.SOON, r.urgency)
        val later = brakeFluidReminder(CarCare("a", lastBrakeFluid = YearMonth.of(2024, 12)), today)!!
        assertEquals(Urgency.LATER, later.urgency)
    }

    // --- Zahnriemen ---

    @Test
    fun timingBeltByAgeFromBuildYear() {
        val care = CarCare("a", beltYears = 6, beltKm = 120_000)
        val r = timingBeltReminder(care, emptyList(), today, odometer = null, buildYear = 2021)!!
        assertEquals(LocalDate.of(2027, 1, 1), r.due)
        assertEquals("Spätestens 2027", r.headline)
    }

    @Test
    fun timingBeltByKmWhenDrivingALot() {
        // 115.000 km, 2.500 km/Monat: 5.000 km übrig → in 2 Monaten, lange vor dem Altersgrenze.
        val care = CarCare("a", beltYears = 10, beltKm = 120_000)
        val r = timingBeltReminder(care, monthly(2_500, YearMonth.of(2026, 4), 6), today, odometer = 115_000, buildYear = 2020)!!
        assertEquals(LocalDate.of(2026, 12, 31), r.due)
        assertEquals(Urgency.SOON, r.urgency)
        assertTrue(r.headline, r.headline.startsWith("Voraussichtlich"))
    }

    @Test
    fun timingBeltCountsFromLastChange() {
        val care = CarCare("a", beltYears = 6, beltKm = 120_000, lastBeltYear = 2024, lastBeltOdometer = 100_000)
        val r = timingBeltReminder(care, emptyList(), today, odometer = 130_000, buildYear = 2012)!!
        assertEquals(LocalDate.of(2030, 1, 1), r.due)
        assertTrue(r.detail, r.detail.contains("bei 220.000 km"))
    }

    @Test
    fun noTimingBeltReminderWithoutAgeOrKm() {
        assertNull(timingBeltReminder(CarCare("a"), emptyList(), today, odometer = null, buildYear = null))
    }
}
