package de.kilometerbuch.data

/** Ein Auto. [colorIndex] bleibt fest, damit das Auto in Diagrammen immer dieselbe Farbe hat. */
data class Car(
    val id: String,
    val name: String,
    val colorIndex: Int,
)

const val CAR_COLOR_COUNT = 8

/** Kleinste noch freie Farbe; sind alle vergeben, geht es reihum weiter. */
fun nextColorIndex(cars: List<Car>): Int =
    (0 until CAR_COLOR_COUNT).firstOrNull { i -> cars.none { it.colorIndex == i } } ?: (cars.size % CAR_COLOR_COUNT)
