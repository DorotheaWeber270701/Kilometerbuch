package de.kilometerbuch.ui

import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.estimateOdometer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil

/** Die Erinnerungen, die man pro Auto ein- und ausschalten kann. */
enum class CareItem(val title: String, val description: String, val needs: List<String>) {
    HU(
        "Hauptuntersuchung (TÜV)",
        "Erinnert rechtzeitig vor dem Monat auf der Plakette. Einen gebuchten Termin kannst du eintragen, " +
            "dann kommt die Erinnerung am Tag vorher.",
        listOf("Monat auf der HU-Plakette"),
    ),
    SERVICE(
        "Inspektion",
        "Nach Zeit oder Kilometern, je nachdem, was zuerst kommt. Die Kilometer kommen aus deinen Monatseinträgen.",
        listOf("Monat der letzten Inspektion", "Intervall laut Serviceheft", "Optional: km-Stand bei der letzten Inspektion"),
    ),
    TIRES(
        "Reifenwechsel",
        "Im Oktober Winterreifen, nach Ostern Sommerreifen (Faustregel „von O bis O“). Aus bei Ganzjahresreifen.",
        emptyList(),
    ),
    BRAKE_FLUID(
        "Bremsflüssigkeit",
        "Bremsflüssigkeit zieht Wasser und muss meist alle 2 Jahre gewechselt werden, oft zusammen mit der Inspektion.",
        listOf("Monat des letzten Wechsels"),
    ),
    TIMING_BELT(
        "Zahnriemen",
        "Nach Kilometern oder Jahren laut Hersteller. Nur bei Motoren mit Zahnriemen, nicht bei Steuerkette.",
        listOf("Baujahr oder Jahr des letzten Wechsels", "Intervall laut Serviceheft", "Optional: Kilometerstand (genauer)"),
    ),
}

fun CarCare.isOn(item: CareItem): Boolean = when (item) {
    CareItem.HU -> huOn
    CareItem.SERVICE -> serviceOn
    CareItem.TIRES -> tiresOn
    CareItem.BRAKE_FLUID -> brakeOn
    CareItem.TIMING_BELT -> beltOn
}

fun CarCare.withOn(item: CareItem, on: Boolean): CarCare = when (item) {
    CareItem.HU -> copy(huOn = on)
    CareItem.SERVICE -> copy(serviceOn = on)
    CareItem.TIRES -> copy(tiresOn = on)
    CareItem.BRAKE_FLUID -> copy(brakeOn = on)
    CareItem.TIMING_BELT -> copy(beltOn = on)
}

/** Ob zum Einschalten noch Angaben fehlen; dann wird erst gefragt. */
fun needsSetup(item: CareItem, care: CarCare, car: Car): Boolean = when (item) {
    CareItem.HU -> care.huDue == null
    CareItem.SERVICE -> care.lastService == null
    CareItem.TIRES -> false
    CareItem.BRAKE_FLUID -> care.lastBrakeFluid == null
    CareItem.TIMING_BELT -> care.lastBeltYear == null && car.buildYear == null
}

enum class ReminderKind(val title: String, val item: CareItem) {
    HU("Hauptuntersuchung (TÜV)", CareItem.HU),
    SERVICE("Inspektion", CareItem.SERVICE),
    WINTER_TIRES("Winterreifen aufziehen", CareItem.TIRES),
    SUMMER_TIRES("Sommerreifen aufziehen", CareItem.TIRES),
    BRAKE_FLUID("Bremsflüssigkeit wechseln", CareItem.BRAKE_FLUID),
    TIMING_BELT("Zahnriemen wechseln", CareItem.TIMING_BELT),
}

enum class Urgency { OVERDUE, SOON, LATER }

data class Reminder(
    val carId: String,
    val kind: ReminderKind,
    val due: LocalDate,
    val urgency: Urgency,
    val headline: String,
    val detail: String,
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
        if (care.beltOn) timingBeltReminder(care, own, today, odometer, car.buildYear) else null,
    ).sortedBy { it.due }
}

/** Nach einer HU gilt die nächste 24 Monate ab dem Prüfmonat. */
fun nextHuAfter(doneIn: YearMonth): YearMonth = doneIn.plusMonths(24)

private fun urgencyFor(due: LocalDate, today: LocalDate, soonFrom: LocalDate = due.minusDays(SOON_DAYS)) = when {
    today.isAfter(due) -> Urgency.OVERDUE
    !today.isBefore(soonFrom) -> Urgency.SOON
    else -> Urgency.LATER
}

private fun monthsText(n: Long) = if (n == 1L) "1 Monat" else "$n Monaten"

/** „noch 12 Tage“, „in 5 Monaten“ */
private fun timeLeft(today: LocalDate, due: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, due)
    return when {
        days <= 0 -> "heute"
        days == 1L -> "noch 1 Tag"
        days <= 45 -> "noch $days Tage"
        else -> "in ${monthsText(ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(due)))}"
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

private val WEEKDAY_DATE = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", Locale.GERMAN)

/** „Mi., 14.10.2026“ */
fun fmtWeekdayDate(d: LocalDate): String = d.format(WEEKDAY_DATE)

/** Wahlmöglichkeiten für die Vorwarnzeit vor dem Plaketten-Monat. */
val HU_WARN_OPTIONS = listOf(14 to "2 Wochen", 30 to "1 Monat", 60 to "2 Monate")

// --- HU ---

internal fun huReminder(care: CarCare, today: LocalDate): Reminder? {
    val month = care.huDue ?: return null
    val appointment = care.huAppointment
    if (appointment != null) return huAppointmentReminder(care, month, appointment, today)

    val due = month.atEndOfMonth()
    // Fällig ist der ganze Plaketten-Monat; erinnert wird die eingestellte Zeit vor dessen Beginn.
    val urgency = urgencyFor(due, today, soonFrom = month.atDay(1).minusDays(care.huWarnDays.toLong()))
    val detail = when (urgency) {
        Urgency.OVERDUE -> {
            val over = ChronoUnit.MONTHS.between(month, YearMonth.from(today))
            "Seit ${monthsText(over)} überfällig. Wer mehr als 2 Monate drüber ist, zahlt ein Bußgeld " +
                "und eine teurere Prüfung."
        }
        Urgency.SOON -> "Jetzt einen Termin bei TÜV, DEKRA oder GTÜ ausmachen und hier eintragen. " +
            "Die Abgasuntersuchung (AU) ist Teil der HU."
        Urgency.LATER -> "${timeLeft(today, month.atDay(1)).replaceFirstChar { it.uppercase() }}."
    }
    return Reminder(care.carId, ReminderKind.HU, due, urgency, "Fällig im ${monthLong(month)}", detail)
}

/** HU mit gebuchtem Termin: Es zählt der Termin, nicht mehr der Plaketten-Monat. */
private fun huAppointmentReminder(care: CarCare, month: YearMonth, appointment: LocalDate, today: LocalDate): Reminder {
    val days = ChronoUnit.DAYS.between(today, appointment)
    val urgency = if (days <= 7) Urgency.SOON else Urgency.LATER
    val detail = when {
        days < 0 -> "Der Termin war am ${fmtDate(appointment)}. Tippe auf „Erledigt“, dann wird die nächste HU berechnet."
        days == 0L -> "Heute. Fahrzeugschein (Zulassungsbescheinigung Teil I) nicht vergessen."
        days == 1L -> "Morgen. Fahrzeugschein (Zulassungsbescheinigung Teil I) nicht vergessen."
        else -> "Noch $days Tage. Plakette: ${monthLong(month)}."
    }
    return Reminder(
        care.carId, ReminderKind.HU, appointment, urgency,
        "Termin am ${fmtWeekdayDate(appointment)}", detail, appointment = appointment,
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
        remaining != null && remaining <= 0 -> "Kilometergrenze erreicht"
        urgency == Urgency.OVERDUE -> "Seit ${monthLong(YearMonth.from(timeDue))} fällig"
        byKm -> "Voraussichtlich im ${monthLong(YearMonth.from(due))}"
        else -> "Spätestens im ${monthLong(YearMonth.from(timeDue))}"
    }
    val detail = buildString {
        if (care.serviceKm != null) {
            append("${fmtInt(kmSince.toDouble())} von ${fmtInt(care.serviceKm.toDouble())} km gefahren")
            if (remaining != null && remaining > 0) append(", noch ca. ${fmtInt(remaining.toDouble())} km")
            append(". ")
        }
        append("Letzte Inspektion: ${monthLong(last)}")
        care.lastServiceOdometer?.let { append(" bei ${fmtInt(it.toDouble())} km") }
        append(".")
    }
    return Reminder(care.carId, ReminderKind.SERVICE, due, urgency, headline, detail)
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
    val headline = if (winter) "Im Oktober ${season.due.year}" else "Nach Ostern, meist im April ${season.due.year}"
    val detail = if (winter) {
        "Faustregel: Winterreifen von Oktober bis Ostern. Bei Glätte, Schnee und Eis sind sie Pflicht."
    } else {
        "Faustregel: Winterreifen von Oktober bis Ostern, danach Sommerreifen."
    }
    return Reminder(care.carId, season.kind, season.due, urgency, headline, detail, seasonKey = season.key)
}

// --- Bremsflüssigkeit ---

internal fun brakeFluidReminder(care: CarCare, today: LocalDate): Reminder? {
    val last = care.lastBrakeFluid ?: return null
    val dueMonth = last.plusMonths(care.brakeMonths.toLong())
    val due = dueMonth.atEndOfMonth()
    val urgency = urgencyFor(due, today)
    val headline = if (urgency == Urgency.OVERDUE) "Seit ${monthLong(dueMonth)} fällig" else "Spätestens im ${monthLong(dueMonth)}"
    val detail = "Letzter Wechsel: ${monthLong(last)}. Am einfachsten bei der nächsten Inspektion mitmachen lassen."
    return Reminder(care.carId, ReminderKind.BRAKE_FLUID, due, urgency, headline, detail)
}

// --- Zahnriemen ---

/**
 * Fällig nach [CarCare.beltKm] km oder [CarCare.beltYears] Jahren seit dem letzten Wechsel,
 * ohne Wechsel seit Baujahr und 0 km. Ohne genug Angaben gibt es keine Erinnerung.
 */
internal fun timingBeltReminder(
    care: CarCare,
    entries: List<Entry>,
    today: LocalDate,
    odometer: Int?,
    buildYear: Int?,
): Reminder? {
    val neverChanged = care.lastBeltYear == null && care.lastBeltOdometer == null
    val baseYear = care.lastBeltYear ?: buildYear
    val baseKm = care.lastBeltOdometer ?: if (neverChanged) 0 else null
    val remaining = if (baseKm != null && odometer != null) baseKm + care.beltKm - odometer else null
    if (baseYear == null && remaining == null) return null

    // Nach Alter: sicherheitshalber ab Jahresbeginn des Fälligkeitsjahres.
    val dueYear = baseYear?.plus(care.beltYears)
    val timeDue = dueYear?.let { LocalDate.of(it, 1, 1) }
    val avgPerMonth = averageMonthlyKm(entries)
    val kmDue = remaining?.let { projectKmDue(it, avgPerMonth, today) }
    val due = listOfNotNull(timeDue, kmDue).minOrNull() ?: return null
    val byKm = kmDue != null && due == kmDue && due != timeDue

    // Ein Zahnriemenwechsel will geplant sein: „bald“ schon 2 Monate vorher.
    var urgency = if (remaining != null && remaining <= 0) Urgency.OVERDUE else urgencyFor(due, today, due.minusDays(60))
    if (urgency == Urgency.LATER && remaining != null && avgPerMonth != null && remaining <= 2 * avgPerMonth) {
        urgency = Urgency.SOON
    }

    val headline = when {
        remaining != null && remaining <= 0 -> "Kilometergrenze erreicht"
        byKm -> "Voraussichtlich im ${monthLong(YearMonth.from(due))}"
        urgency == Urgency.OVERDUE -> "Seit $dueYear fällig"
        else -> "Spätestens $dueYear"
    }
    val detail = buildString {
        val limits = listOfNotNull(
            baseKm?.let { "bei ${fmtInt((it + care.beltKm).toDouble())} km" },
            dueYear?.let { "im Jahr $it" },
        )
        append("Fällig ${limits.joinToString(" oder ")}, je nachdem, was zuerst kommt.")
        odometer?.let { append(" Aktuell ca. ${fmtInt(it.toDouble())} km.") }
        if (neverChanged) append(" Gerechnet ab Baujahr, weil noch kein Wechsel eingetragen ist.")
    }
    return Reminder(care.carId, ReminderKind.TIMING_BELT, due, urgency, headline, detail)
}
