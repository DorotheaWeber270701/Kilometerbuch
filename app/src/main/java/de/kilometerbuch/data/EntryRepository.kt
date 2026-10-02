package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/** Monatseinträge (Kilometer und Verbrauch). */
class EntryRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "eintraege.json")

    /** [defaultCarId] gilt für Einträge aus der Zeit vor mehreren Autos. */
    fun load(defaultCarId: String): List<Entry> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("entries")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(
                carId = o.optString("carId", defaultCarId),
                month = YearMonth.parse(o.getString("month")),
                km = o.getInt("km"),
                l100 = if (o.isNull("l100")) null else o.getDouble("l100"),
            )
        }.sortedBy { it.month }
    }

    fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(JSONObject().apply {
                put("carId", e.carId)
                put("month", e.month.toString())
                put("km", e.km)
                if (e.l100 != null) put("l100", e.l100)
            })
        }
        store.write(JSONObject().put("version", 2).put("entries", arr))
    }
}
