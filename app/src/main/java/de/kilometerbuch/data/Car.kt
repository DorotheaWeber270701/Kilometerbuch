package de.kilometerbuch.data

import java.time.YearMonth

/**
 * Ein Auto. [colorIndex] wählt eine Farbe aus der festen Palette; [customColor] (ARGB) ersetzt sie,
 * wenn eine eigene Farbe gewählt wurde. Der Kilometerstand wird ab [odometerMonth] mit den
 * Monatseinträgen fortgeschrieben.
 */
data class Car(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val odometerKm: Int? = null,
    /** Monat, bis zu dem [odometerKm] gilt; spätere Monatseinträge kommen dazu. */
    val odometerMonth: YearMonth? = null,
    val buildYear: Int? = null,
    val customColor: Int? = null,
)

const val CAR_COLOR_COUNT = 8

/** Kleinste noch freie Farbe; sind alle vergeben, geht es reihum weiter. */
fun nextColorIndex(cars: List<Car>): Int =
    (0 until CAR_COLOR_COUNT).firstOrNull { i -> cars.none { it.colorIndex == i && it.customColor == null } }
        ?: (cars.size % CAR_COLOR_COUNT)

/** Heutiger Kilometerstand: abgelesener Stand plus die Monatskilometer seitdem. null, wenn nie abgelesen. */
fun estimateOdometer(car: Car, entries: List<Entry>): Int? {
    val km = car.odometerKm ?: return null
    val from = car.odometerMonth ?: return km
    return km + entries.filter { it.carId == car.id && it.month.isAfter(from) }.sumOf { it.km }
}

val ODOMETER_RANGE = 0..2_000_000

/**
 * Gleicht den Kilometerstand mit dem echten Tachostand [actual] ab: Die Differenz zur Schätzung
 * wird dem Monat [month] zugeschlagen (bei weniger Kilometern abgezogen, nie unter 0).
 * Danach gilt [actual] als Stand nach dem letzten eingetragenen Monat, die Schätzung stimmt also wieder.
 * Gibt das geänderte Auto und alle Einträge zurück.
 */
fun correctOdometer(car: Car, entries: List<Entry>, actual: Int, month: YearMonth): Pair<Car, List<Entry>> {
    val estimate = estimateOdometer(car, entries)
    var updated = entries
    if (estimate != null && actual != estimate) {
        val diff = actual - estimate
        val existing = entries.find { it.carId == car.id && it.month == month }
        updated = when {
            existing != null -> entries.map {
                if (it === existing) it.copy(km = (it.km + diff).coerceAtLeast(0)) else it
            }
            diff > 0 -> (entries + Entry(car.id, month, diff, null)).sortedBy { it.month }
            else -> entries
        }
    }
    // Der abgelesene Stand enthält alle bisher eingetragenen Monate.
    val anchor = (updated.filter { it.carId == car.id }.map { it.month } + month).max()
    return car.copy(odometerKm = actual, odometerMonth = anchor) to updated
}
