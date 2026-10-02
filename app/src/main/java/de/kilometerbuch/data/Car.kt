package de.kilometerbuch.data

import java.time.YearMonth

/**
 * Ein Auto. [colorIndex] bleibt fest, damit das Auto in Diagrammen immer dieselbe Farbe hat.
 * Der Kilometerstand wird ab [odometerMonth] mit den Monatseinträgen fortgeschrieben.
 */
data class Car(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val odometerKm: Int? = null,
    /** Monat, in dem [odometerKm] abgelesen wurde. */
    val odometerMonth: YearMonth? = null,
    val buildYear: Int? = null,
)

const val CAR_COLOR_COUNT = 8

/** Kleinste noch freie Farbe; sind alle vergeben, geht es reihum weiter. */
fun nextColorIndex(cars: List<Car>): Int =
    (0 until CAR_COLOR_COUNT).firstOrNull { i -> cars.none { it.colorIndex == i } } ?: (cars.size % CAR_COLOR_COUNT)

/** Heutiger Kilometerstand: abgelesener Stand plus die Monatskilometer seitdem. null, wenn nie abgelesen. */
fun estimateOdometer(car: Car, entries: List<Entry>): Int? {
    val km = car.odometerKm ?: return null
    val from = car.odometerMonth ?: return km
    return km + entries.filter { it.carId == car.id && it.month.isAfter(from) }.sumOf { it.km }
}

val ODOMETER_RANGE = 0..2_000_000
