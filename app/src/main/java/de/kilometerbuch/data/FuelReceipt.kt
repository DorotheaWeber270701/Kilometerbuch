package de.kilometerbuch.data

import java.time.LocalDate

/** Ein Tankbeleg. Der Literpreis wird immer berechnet, nie eingegeben. */
data class FuelReceipt(
    val id: String,
    val carId: String,
    val date: LocalDate,
    val liters: Double,
    val total: Double,
) {
    val pricePerLiter: Double get() = total / liters
}
