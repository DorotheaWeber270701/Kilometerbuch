package de.kilometerbuch.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** Termin-Angaben je Auto. */
class CareRepository(context: Context) {

    private val store = JsonFileStore(context.filesDir, "termine.json")

    fun load(): List<CarCare> = store.load(emptyList()) { root ->
        val arr = root.getJSONArray("cars")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val huDue = o.optStringOrNull("huDue")?.let(YearMonth::parse)
            val lastService = o.optStringOrNull("lastService")?.let(YearMonth::parse)
            CarCare(
                carId = o.getString("carId"),
                // Version 1 kannte keine Schalter: vorhandene Angaben bedeuteten „ein“.
                huOn = o.optBoolean("huOn", huDue != null),
                huDue = huDue,
                huAppointment = o.optStringOrNull("huAppointment")?.let(LocalDate::parse),
                huWarnDays = o.optInt("huWarnDays", 30),
                serviceOn = o.optBoolean("serviceOn", lastService != null),
                lastService = lastService,
                lastServiceOdometer = o.optIntOrNull("lastServiceOdometer"),
                serviceMonths = o.optInt("serviceMonths", 12),
                serviceKm = o.optIntOrNull("serviceKm"),
                tiresOn = o.optBoolean("tiresOn", o.optBoolean("seasonalTires", false)),
                tiresDone = o.optStringOrNull("tiresDone"),
                brakeOn = o.optBoolean("brakeOn", false),
                lastBrakeFluid = o.optStringOrNull("lastBrakeFluid")?.let(YearMonth::parse),
                brakeMonths = o.optInt("brakeMonths", 24),
                beltOn = o.optBoolean("beltOn", false),
                beltKm = o.optInt("beltKm", 120_000),
                beltYears = o.optInt("beltYears", 6),
                lastBeltOdometer = o.optIntOrNull("lastBeltOdometer"),
                lastBeltYear = o.optIntOrNull("lastBeltYear"),
            )
        }
    }

    fun save(cares: List<CarCare>) {
        val arr = JSONArray()
        cares.forEach { c ->
            arr.put(JSONObject().apply {
                put("carId", c.carId)
                put("huOn", c.huOn)
                c.huDue?.let { put("huDue", it.toString()) }
                c.huAppointment?.let { put("huAppointment", it.toString()) }
                put("huWarnDays", c.huWarnDays)
                put("serviceOn", c.serviceOn)
                c.lastService?.let { put("lastService", it.toString()) }
                c.lastServiceOdometer?.let { put("lastServiceOdometer", it) }
                put("serviceMonths", c.serviceMonths)
                c.serviceKm?.let { put("serviceKm", it) }
                put("tiresOn", c.tiresOn)
                c.tiresDone?.let { put("tiresDone", it) }
                put("brakeOn", c.brakeOn)
                c.lastBrakeFluid?.let { put("lastBrakeFluid", it.toString()) }
                put("brakeMonths", c.brakeMonths)
                put("beltOn", c.beltOn)
                put("beltKm", c.beltKm)
                put("beltYears", c.beltYears)
                c.lastBeltOdometer?.let { put("lastBeltOdometer", it) }
                c.lastBeltYear?.let { put("lastBeltYear", it) }
            })
        }
        store.write(JSONObject().put("version", 2).put("cars", arr))
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
}
