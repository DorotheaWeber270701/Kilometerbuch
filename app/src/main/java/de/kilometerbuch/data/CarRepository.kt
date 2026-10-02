package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

class CarRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "autos.json")

    fun load(): List<Car> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("cars")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Car(
                id = o.getString("id"),
                name = o.getString("name"),
                colorIndex = o.getInt("color"),
                odometerKm = if (o.isNull("odometerKm")) null else o.getInt("odometerKm"),
                odometerMonth = if (o.isNull("odometerMonth")) null else YearMonth.parse(o.getString("odometerMonth")),
                buildYear = if (o.isNull("buildYear")) null else o.getInt("buildYear"),
            )
        }
    }

    fun save(cars: List<Car>) {
        val arr = JSONArray()
        cars.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("color", c.colorIndex)
                c.odometerKm?.let { put("odometerKm", it) }
                c.odometerMonth?.let { put("odometerMonth", it.toString()) }
                c.buildYear?.let { put("buildYear", it) }
            })
        }
        store.write(JSONObject().put("version", 2).put("cars", arr))
    }
}
