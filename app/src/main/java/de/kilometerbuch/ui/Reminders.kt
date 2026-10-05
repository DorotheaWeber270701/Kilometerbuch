package de.kilometerbuch.ui

import androidx.annotation.StringRes
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.i18n.UiText
import de.kilometerbuch.i18n.plural
import de.kilometerbuch.i18n.text
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Die Erinnerungen, die man pro Auto ein- und ausschalten kann. */
enum class CareItem(@StringRes val title: Int, @StringRes val description: Int, val needs: List<Int>) {
    HU(R.string.care_hu_title, R.string.care_hu_desc, listOf(R.string.care_hu_need)),
    SERVICE(
        R.string.care_service_title,
        R.string.care_service_desc,
        listOf(R.string.care_service_need_month, R.string.care_service_need_interval, R.string.care_service_need_odo),
    ),
    TIRES(R.string.care_tires_title, R.string.care_tires_desc, emptyList()),
    BRAKE_FLUID(R.string.care_brake_title, R.string.care_brake_desc, listOf(R.string.care_brake_need)),
}

fun CarCare.isOn(item: CareItem): Boolean = when (item) {
    CareItem.HU -> huOn
    CareItem.SERVICE -> serviceOn
    CareItem.TIRES -> tiresOn
    CareItem.BRAKE_FLUID -> brakeOn
}

fun CarCare.withOn(item: CareItem, on: Boolean): CarCare = when (item) {
    CareItem.HU -> copy(huOn = on)
    CareItem.SERVICE -> copy(serviceOn = on)
    CareItem.TIRES -> copy(tiresOn = on)
    CareItem.BRAKE_FLUID -> copy(brakeOn = on)
}

/** Ob zum Einschalten noch Angaben fehlen; dann wird erst gefragt. */
fun needsSetup(item: CareItem, care: CarCare): Boolean = when (item) {
    CareItem.HU -> care.huDue == null
    CareItem.SERVICE -> care.lastService == null
    CareItem.TIRES -> false
    CareItem.BRAKE_FLUID -> care.lastBrakeFluid == null
}

enum class ReminderKind(@StringRes val title: Int, val item: CareItem) {
    HU(R.string.care_hu_title, CareItem.HU),
    SERVICE(R.string.care_service_title, CareItem.SERVICE),
    WINTER_TIRES(R.string.kind_winter_tires, CareItem.TIRES),
    SUMMER_TIRES(R.string.kind_summer_tires, CareItem.TIRES),
    BRAKE_FLUID(R.string.kind_brake_fluid, CareItem.BRAKE_FLUID),
}

enum class Urgency { OVERDUE, SOON, LATER }

data class Reminder(
    val carId: String,
    val kind: ReminderKind,
    val due: LocalDate,
    val urgency: Urgency,
    val headline: UiText,
    val detail: UiText,
    /** Nur beim Reifenwechsel: die Saison, die „Erledigt“ abhakt. */
    val seasonKey: String? = null,
    /** Nur bei der HU: der gebuchte Prüftermin. */
    val appointment: LocalDate? = null,
) {
    /** Eindeutig je Termin; ändert sich, sobald ein neuer Termin berechnet wird. */
    val key: String get() = "$carId/$kind/$due"
}

/** Ab so vielen Tagen vor dem Termin gilt er als „bald fällig“. */
const val SOON_DAYS = 30L

/** Alle eingeschalteten Erinnerungen eines Autos, die früheste zuerst. */
fun reminders(car: Car, care: CarCare, entries: List<Entry>, today: LocalDate): List<Reminder> {
    val own = entries.filter { it.carId == car.id }
    val odometer = estimateOdometer(car, own)
    return listOfNotNull(
        if (care.huOn) huReminder(care, today) else null,
        if (care.serviceOn) serviceReminder(care, own, today, odometer) else null,
        if (care.tiresOn) tireReminder(care, today) else null,
        if (care.brakeOn) brakeFluidReminder(care, today) else null,
    ).sortedBy { it.due }
}

/** Nach einer HU gilt die nächste 24 Monate ab dem Prüfmonat. */
fun nextHuAfter(doneIn: YearMonth): YearMonth = doneIn.plusMonths(24)

/** Vorwarnzeit vor dem Plaketten-Monat in Tagen, mit Beschriftung. */
val HU_WARN_OPTIONS = listOf(14 to R.string.warn_2_weeks, 30 to R.string.warn_1_month, 60 to R.string.warn_2_months)

private fun urgencyFor(due: LocalDate, today: LocalDate, soonFrom: LocalDate = due.minusDays(SOON_DAYS)) = when {
    today.isAfter(due) -> Urgency.OVERDUE
    !today.isBefore(soonFrom) -> Urgency.SOON
    else -> Urgency.LATER
}

/** „Noch 12 Tage.“ oder „In 5 Monaten.“ */
private fun timeLeft(today: LocalDate, due: LocalDate): UiText {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days <= 0 -> text(R.string.today_sentence)
        days <= 45 -> plural(R.plurals.days_left, days.toInt())
        else -> plural(R.plurals.months_left, ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(due)).toInt())
    }
}

/** Durchschnittliche Monatskilometer der letzten 6 Einträge; null ohne Einträge. */
private fun averageMonthlyKm(entries: List<Entry>): Double? {
    val recent = entries.sortedByDescending { it.month }.take(6).map { it.km }.filter { it > 0 }
    return if (recent.isEmpty()) null else recent.average()
}

/** Monat, in dem [remaining] km bei [avgPerMonth] erreicht sind. */
private fun projectKmDue(remaining: Int, avgPerMonth: Double?, today: LocalDate): LocalDate? = when {
    remaining <= 0 -> today
    avgPerMonth == null -> null
    else -> YearMonth.from(today).plusMonths(ceil(remaining / avgPerMonth).toLong()).atEndOfMonth()
}

// --- HU ---

internal fun huReminder(care: CarCare, today: LocalDate): Reminder? {
    val month = care.huDue ?: return null
    val appointment = care.huAppointment
    if (appointment != null) return huAppointmentReminder(care, month, appointment, today)

    val due = month.atEndOfMonth()
    // Fällig ist der ganze Plaketten-Monat; erinnert wird die eingestellte Zeit vor dessen Beginn.
    val urgency = urgencyFor(due, today, soonFrom = month.atDay(1).minusDays(care.huWarnDays.toLong()))
    val detail = when (urgency) {
        Urgency.OVERDUE -> UiText.Join(
            listOf(
                plural(R.plurals.hu_overdue_months, ChronoUnit.MONTHS.between(month, YearMonth.from(today)).toInt()),
                text(R.string.hu_overdue_fine),
            ),
        )
        Urgency.SOON -> text(R.string.hu_soon)
        Urgency.LATER -> timeLeft(today, month.atDay(1))
    }
    return Reminder(care.carId, ReminderKind.HU, due, urgency, text(R.string.due_in, month), detail)
}

/** HU mit gebuchtem Termin: Es zählt der Termin, nicht mehr der Plaketten-Monat. */
private fun huAppointmentReminder(care: CarCare, month: YearMonth, appointment: LocalDate, today: LocalDate): Reminder {
    val days = ChronoUnit.DAYS.between(today, appointment)
    val urgency = if (days <= 7) Urgency.SOON else Urgency.LATER
    val detail = when {
        days < 0 -> text(R.string.hu_appt_past, appointment)
        days == 0L -> text(R.string.hu_appt_today)
        days == 1L -> text(R.string.hu_appt_tomorrow)
        else -> UiText.Join(listOf(plural(R.plurals.days_left, days.toInt()), text(R.string.hu_plakette, month)))
    }
    return Reminder(
        care.carId, ReminderKind.HU, appointment, urgency,
        text(R.string.hu_appt_headline, UiText.WeekdayDate(appointment)), detail, appointment = appointment,
    )
}

// --- Inspektion ---

/**
 * [odometer] ist der geschätzte heutige Kilometerstand. Mit km-Stand bei der letzten Inspektion
 * wird daraus gerechnet, sonst aus den Monatseinträgen seit der Inspektion.
 */
internal fun serviceReminder(care: CarCare, entries: List<Entry>, today: LocalDate, odometer: Int? = null): Reminder? {
    val last = care.lastService ?: return null
    val timeDue = last.plusMonths(care.serviceMonths.toLong()).atEndOfMonth()

    val kmSince = if (odometer != null && care.lastServiceOdometer != null) {
        (odometer - care.lastServiceOdometer).coerceAtLeast(0)
    } else {
        // Kilometer zählen ab dem Monat nach der Inspektion.
        entries.filter { it.month.isAfter(last) }.sumOf { it.km }
    }
    val avgPerMonth = averageMonthlyKm(entries)
    val remaining = care.serviceKm?.let { it - kmSince }
    val kmDue = remaining?.let { projectKmDue(it, avgPerMonth, today) }

    val due = if (kmDue != null && kmDue.isBefore(timeDue)) kmDue else timeDue
    val byKm = due != timeDue

    var urgency = if (remaining != null && remaining <= 0) Urgency.OVERDUE else urgencyFor(due, today)
    if (urgency == Urgency.LATER && remaining != null && avgPerMonth != null && remaining <= avgPerMonth) {
        urgency = Urgency.SOON
    }

    val headline = when {
        remaining != null && remaining <= 0 -> text(R.string.km_limit_reached)
        urgency == Urgency.OVERDUE -> text(R.string.due_since, YearMonth.from(timeDue))
        byKm -> text(R.string.expected_in, YearMonth.from(due))
        else -> text(R.string.latest_in, YearMonth.from(timeDue))
    }
    val parts = mutableListOf<UiText>()
    if (care.serviceKm != null) {
        parts += if (remaining != null && remaining > 0) {
            text(R.string.service_progress_left, fmtInt(kmSince.toDouble()), fmtInt(care.serviceKm.toDouble()), fmtInt(remaining.toDouble()))
        } else {
            text(R.string.service_progress, fmtInt(kmSince.toDouble()), fmtInt(care.serviceKm.toDouble()))
        }
    }
    parts += care.lastServiceOdometer?.let { text(R.string.service_last_at, last, fmtInt(it.toDouble())) }
        ?: text(R.string.service_last, last)
    return Reminder(care.carId, ReminderKind.SERVICE, due, urgency, headline, UiText.Join(parts))
}

// --- Reifen ---

private data class TireSeason(
    val kind: ReminderKind,
    val key: String,
    val start: LocalDate,
    val due: LocalDate,
    val end: LocalDate,
)

/** Faustregel „von O bis O“: Winterreifen im Oktober, Sommerreifen nach Ostern. */
internal fun tireReminder(care: CarCare, today: LocalDate): Reminder? {
    val seasons = (today.year - 1..today.year + 1).flatMap { y ->
        listOf(
            TireSeason(ReminderKind.SUMMER_TIRES, "$y-sommer", LocalDate.of(y, 3, 15), LocalDate.of(y, 4, 30), LocalDate.of(y, 5, 31)),
            TireSeason(ReminderKind.WINTER_TIRES, "$y-winter", LocalDate.of(y, 9, 15), LocalDate.of(y, 10, 31), LocalDate.of(y, 11, 30)),
        )
    }
    // Die nächste Saison, die noch läuft oder kommt und nicht abgehakt ist.
    val season = seasons
        .filter { it.key != care.tiresDone && !today.isAfter(it.end) }
        .minByOrNull { it.start } ?: return null

    val urgency = when {
        today.isBefore(season.start) -> Urgency.LATER
        !today.isAfter(season.due) -> Urgency.SOON
        else -> Urgency.OVERDUE
    }
    val winter = season.kind == ReminderKind.WINTER_TIRES
    val headline = text(if (winter) R.string.tires_winter_headline else R.string.tires_summer_headline, season.due.year)
    val detail = text(if (winter) R.string.tires_winter_detail else R.string.tires_summer_detail)
    return Reminder(care.carId, season.kind, season.due, urgency, headline, detail, seasonKey = season.key)
}

// --- Bremsflüssigkeit ---

internal fun brakeFluidReminder(care: CarCare, today: LocalDate): Reminder? {
    val last = care.lastBrakeFluid ?: return null
    val dueMonth = last.plusMonths(care.brakeMonths.toLong())
    val due = dueMonth.atEndOfMonth()
    val urgency = urgencyFor(due, today)
    val headline = text(if (urgency == Urgency.OVERDUE) R.string.due_since else R.string.latest_in, dueMonth)
    return Reminder(care.carId, ReminderKind.BRAKE_FLUID, due, urgency, headline, text(R.string.brake_detail, last))
}
