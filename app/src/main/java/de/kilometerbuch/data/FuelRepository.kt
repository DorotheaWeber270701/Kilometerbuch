package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Tankbelege. */
class FuelRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "tankbelege.json")

    /** [defaultCarId] gilt für Belege aus der Zeit vor mehreren Autos. */
    fun load(defaultCarId: String): List<FuelReceipt> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("receipts")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FuelReceipt(
                id = o.getString("id"),
                carId = o.optString("carId", defaultCarId),
                date = LocalDate.parse(o.getString("date")),
                liters = o.getDouble("liters"),
                total = o.getDouble("total"),
            )
        }.sortedWith(RECEIPT_ORDER)
    }

    fun save(receipts: List<FuelReceipt>) {
        val arr = JSONArray()
        receipts.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("carId", r.carId)
                put("date", r.date.toString())
                put("liters", r.liters)
                put("total", r.total)
            })
        }
        store.write(JSONObject().put("version", 2).put("receipts", arr))
    }

    companion object {
        val RECEIPT_ORDER: Comparator<FuelReceipt> = compareBy({ it.date }, { it.id })
    }
}
