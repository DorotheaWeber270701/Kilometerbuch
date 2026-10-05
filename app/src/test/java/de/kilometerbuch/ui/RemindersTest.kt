package de.kilometerbuch.ui

import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.i18n.UiText
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

    /** Alle Text-Ids in einem Text, auch in zusammengesetzten. */
    private fun ids(text: UiText): List<Int> = when (text) {
        is UiText.Res -> listOf(text.id) + text.args.filterIsInstance<UiText>().flatMap(::ids)
        is UiText.Plural -> listOf(text.id)
        is UiText.WeekdayDate -> emptyList()
        is UiText.Join -> text.parts.flatMap(::ids)
    }

    /** Alle Argumente in einem Text, auch in zusammengesetzten. */
    private fun args(text: UiText): List<Any> = when (text) {
        is UiText.Res -> text.args
        is UiText.Plural -> text.args
        is UiText.WeekdayDate -> listOf(text.date)
        is UiText.Join -> text.parts.flatMap(::args)
    }

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
        val overdue = (r.detail as UiText.Join).parts.first() as UiText.Plural
        assertEquals(R.plurals.hu_overdue_months, overdue.id)
        assertEquals(1, overdue.count)
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
        assertEquals(listOf(R.string.hu_appt_headline), ids(r.headline))
    }

    @Test
    fun appointmentWithinAWeekIsSoon() {
        val care = CarCare("a", huDue = YearMonth.of(2026, 10), huAppointment = LocalDate.of(2026, 10, 3))
        val r = huReminder(care, today)!!
        assertEquals(Urgency.SOON, r.urgency)
        assertEquals(listOf(R.string.hu_appt_tomorrow), ids(r.detail))
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
        assertEquals(listOf(R.string.latest_in), ids(r.headline))
        assertTrue(args(r.detail).toString(), fmtInt(3000.0) in args(r.detail))
    }

    @Test
    fun serviceDueByKmWhenDrivingALot() {
        // 2.000 km/Monat seit April: 12.000 km gefahren, 3.000 übrig → in 2 Monaten.
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), serviceMonths = 12, serviceKm = 15_000)
        val r = serviceReminder(care, monthly(2_000, YearMonth.of(2026, 4), 6), today)!!
        assertEquals(LocalDate.of(2026, 12, 31), r.due)
        assertEquals(listOf(R.string.expected_in), ids(r.headline))
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
        assertEquals(listOf(R.string.km_limit_reached), ids(r.headline))
    }

    @Test
    fun serviceMonthItselfDoesNotCount() {
        // Der Inspektionsmonat zählt nicht mit, erst die Monate danach.
        val care = CarCare("a", lastService = YearMonth.of(2026, 4), serviceKm = 15_000)
        val r = serviceReminder(care, monthly(1_000, YearMonth.of(2026, 4), 3), today)!!
        assertTrue(args(r.detail).toString(), fmtInt(2000.0) in args(r.detail))
    }

    @Test
    fun serviceUsesOdometerWhenServiceKmKnown() {
        val care = CarCare("a", lastService = YearMonth.of(2026, 3), lastServiceOdometer = 40_000, serviceKm = 15_000)
        val r = serviceReminder(care, monthly(1_000, YearMonth.of(2026, 4), 6), today, odometer = 52_000)!!
        assertTrue(args(r.detail).toString(), fmtInt(12000.0) in args(r.detail))
        assertTrue(ids(r.detail).toString(), R.string.service_last_at in ids(r.detail))
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

    // --- Bremsflüssigkeit ---

    @Test
    fun brakeFluidDueTwoYearsAfterLastChange() {
        val r = brakeFluidReminder(CarCare("a", lastBrakeFluid = YearMonth.of(2024, 10)), today)!!
        assertEquals(LocalDate.of(2026, 10, 31), r.due)
        assertEquals(Urgency.SOON, r.urgency)
        val later = brakeFluidReminder(CarCare("a", lastBrakeFluid = YearMonth.of(2024, 12)), today)!!
        assertEquals(Urgency.LATER, later.urgency)
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
}
