package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CarRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "autos.json")

    fun load(): List<Car> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("cars")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Car(id = o.getString("id"), name = o.getString("name"), colorIndex = o.getInt("color"))
        }
    }

    fun save(cars: List<Car>) {
        val arr = JSONArray()
        cars.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("color", c.colorIndex))
        }
        store.write(JSONObject().put("version", 1).put("cars", arr))
    }
}
