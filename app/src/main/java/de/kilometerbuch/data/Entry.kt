package de.kilometerbuch.data

import java.time.YearMonth

/** Ein Monat eines Autos: gefahrene Kilometer und optional der Durchschnittsverbrauch. */
data class Entry(
    val carId: String,
    val month: YearMonth,
    val km: Int,
    val l100: Double?,
)
