package de.kilometerbuch.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * Termin-Erinnerungen eines Autos. Jede Erinnerung hat einen eigenen Schalter; ausgeschaltet
 * bleiben ihre Angaben erhalten. Alles Weitere wird aus den Angaben und den Monatskilometern berechnet.
 */
data class CarCare(
    val carId: String,

    val huOn: Boolean = false,
    /** Monat der nächsten Hauptuntersuchung, wie auf der Plakette. */
    val huDue: YearMonth? = null,
    /** Gebuchter Prüftermin, falls schon vereinbart. */
    val huAppointment: LocalDate? = null,
    /** So viele Tage vor Beginn des Plaketten-Monats wird erinnert. */
    val huWarnDays: Int = 30,

    val serviceOn: Boolean = false,
    val lastService: YearMonth? = null,
    /** Kilometerstand bei der letzten Inspektion, falls bekannt (genauer als die Monatssummen). */
    val lastServiceOdometer: Int? = null,
    val serviceMonths: Int = 12,
    /** null = Inspektion nur nach Zeit. */
    val serviceKm: Int? = 15_000,

    /** Sommer- und Winterreifen wechseln. */
    val tiresOn: Boolean = false,
    /** Saison, deren Reifenwechsel schon erledigt ist, z. B. „2026-winter“. */
    val tiresDone: String? = null,

    val brakeOn: Boolean = false,
    val lastBrakeFluid: YearMonth? = null,
    val brakeMonths: Int = 24,

    val beltOn: Boolean = false,
    val beltKm: Int = 120_000,
    val beltYears: Int = 6,
    /** Letzter Zahnriemenwechsel; beide null = noch nie gewechselt, dann zählen 0 km und das Baujahr. */
    val lastBeltOdometer: Int? = null,
    val lastBeltYear: Int? = null,
) {
    val anyOn: Boolean get() = huOn || serviceOn || tiresOn || brakeOn || beltOn
}
