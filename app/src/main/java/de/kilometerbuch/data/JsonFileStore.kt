package de.kilometerbuch.data

import androidx.core.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Eine JSON-Datei im privaten App-Speicher, atomar geschrieben. */
class JsonFileStore(private val dir: File, private val name: String) {

    private val file = AtomicFile(File(dir, name))

    fun <T> load(empty: T, parse: (JSONObject) -> T): T {
        if (!file.baseFile.exists()) return empty
        return try {
            parse(JSONObject(file.readFully().decodeToString()))
        } catch (e: Exception) {
            // Defekte Datei beiseitelegen statt sie beim nächsten Speichern zu überschreiben.
            file.baseFile.copyTo(File(dir, "${name.removeSuffix(".json")}-defekt-${System.currentTimeMillis()}.json"))
            empty
        }
    }

    fun write(json: JSONObject) {
        val out = file.startWrite()
        try {
            out.write(json.toString(2).toByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }
}
