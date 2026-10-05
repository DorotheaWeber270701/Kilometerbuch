package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Art einer Werkstattrechnung. [code] ist der feste Name in Dateien und in der CSV-Datei. */
enum class MaintenanceCategory(val code: String) {
    INSPECTION("Inspektion"),
    REPAIR("Reparatur"),
    TIRES("Reifen"),
    HU("HU"),
    OTHER("Sonstiges"),
    ;

    companion object {
        fun fromCode(code: String): MaintenanceCategory? =
            entries.find { it.code.equals(code.trim(), ignoreCase = true) || it.name.equals(code.trim(), ignoreCase = true) }
    }
}

/** Kosten für Wartung oder Reparatur, z. B. eine Werkstattrechnung. */
data class MaintenanceCost(
    val id: String,
    val carId: String,
    val date: LocalDate,
    val category: MaintenanceCategory,
    val amount: Double,
    val note: String? = null,
)

val AMOUNT_RANGE = 0.01..50_000.0

class MaintenanceRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "wartung.json")

    fun load(): List<MaintenanceCost> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("costs")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            MaintenanceCost(
                id = o.getString("id"),
                carId = o.getString("carId"),
                date = LocalDate.parse(o.getString("date")),
                category = MaintenanceCategory.fromCode(o.getString("category")) ?: MaintenanceCategory.OTHER,
                amount = o.getDouble("amount"),
                note = if (o.isNull("note")) null else o.getString("note"),
            )
        }.sortedWith(ORDER)
    }

    fun save(costs: List<MaintenanceCost>) {
        val arr = JSONArray()
        costs.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("carId", c.carId)
                put("date", c.date.toString())
                put("category", c.category.code)
                put("amount", c.amount)
                c.note?.let { put("note", it) }
            })
        }
        store.write(JSONObject().put("version", 1).put("costs", arr))
    }

    companion object {
        val ORDER: Comparator<MaintenanceCost> = compareBy({ it.date }, { it.id })
    }
}
