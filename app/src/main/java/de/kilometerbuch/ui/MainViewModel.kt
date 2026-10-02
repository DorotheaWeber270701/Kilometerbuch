package de.kilometerbuch.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.CarRepository
import de.kilometerbuch.data.CareRepository
import de.kilometerbuch.data.Csv
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.EntryRepository
import de.kilometerbuch.data.FuelReceipt
import de.kilometerbuch.data.FuelRepository
import de.kilometerbuch.data.Settings
import de.kilometerbuch.ui.theme.ThemeMode
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.data.nextColorIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.math.abs

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    private val carRepo = CarRepository(app)
    private val entryRepo = EntryRepository(app)
    private val fuelRepo = FuelRepository(app)
    private val careRepo = CareRepository(app)
    private val settings = Settings(app)

    // Ein Thread für Dateizugriffe, damit Speichervorgänge in Reihenfolge laufen.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    /** Alle drei sind null, solange die Dateien noch geladen werden. */
    private val _cars = MutableStateFlow<List<Car>?>(null)
    val cars: StateFlow<List<Car>?> = _cars.asStateFlow()

    private val _entries = MutableStateFlow<List<Entry>?>(null)
    val entries: StateFlow<List<Entry>?> = _entries.asStateFlow()

    private val _receipts = MutableStateFlow<List<FuelReceipt>?>(null)
    val receipts: StateFlow<List<FuelReceipt>?> = _receipts.asStateFlow()

    /** Termin-Angaben; ein Auto ohne Eintrag hat noch keine Termine eingerichtet. */
    private val _cares = MutableStateFlow<List<CarCare>?>(null)
    val cares: StateFlow<List<CarCare>?> = _cares.asStateFlow()

    /** Darstellung hell/dunkel; sofort aus den Einstellungen gelesen, damit nichts aufblitzt. */
    private val _themeMode = MutableStateFlow(settings.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Kurze Meldung unten am Bildschirm. */
    private val _snack = MutableStateFlow<String?>(null)
    val snack: StateFlow<String?> = _snack.asStateFlow()

    /** Ergebnis eines CSV-Imports, wird als Dialog gezeigt. */
    private val _importReport = MutableStateFlow<String?>(null)
    val importReport: StateFlow<String?> = _importReport.asStateFlow()

    init {
        viewModelScope.launch(io) {
            var cars = carRepo.load()
            val entries = entryRepo.load(NO_CAR)
            val receipts = fuelRepo.load(NO_CAR)
            // Daten aus der Zeit vor mehreren Autos bekommen ein Auto; eine leere App startet ohne,
            // dann zeigt sie den Schnellstart.
            if (cars.isEmpty() && (entries.isNotEmpty() || receipts.isNotEmpty())) {
                cars = listOf(Car(UUID.randomUUID().toString(), "Mein Auto", 0))
                carRepo.save(cars)
            }
            val ids = cars.map { it.id }.toSet()
            val fallback = cars.firstOrNull()?.id
            // Daten ohne passendes Auto landen beim ersten Auto statt unsichtbar zu werden.
            _entries.value = entries.mapNotNull { if (it.carId in ids) it else fallback?.let { f -> it.copy(carId = f) } }
            _receipts.value = receipts.mapNotNull { if (it.carId in ids) it else fallback?.let { f -> it.copy(carId = f) } }
            _cares.value = careRepo.load().filter { it.carId in ids }
            _cars.value = cars
        }
    }

    // --- Autos ---

    /** Legt ein Auto an; Kilometerstand und Baujahr sind optional. */
    fun addCar(name: String, odometerKm: Int?, buildYear: Int?): Car? {
        val current = _cars.value ?: return null
        val car = Car(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            colorIndex = nextColorIndex(current),
            odometerKm = odometerKm,
            odometerMonth = odometerKm?.let { YearMonth.now() },
            buildYear = buildYear,
        )
        setCars(current + car)
        return car
    }

    /** Ersetzt die Angaben eines vorhandenen Autos (gleiche id). */
    fun updateCar(car: Car) {
        val current = _cars.value ?: return
        setCars(current.map { if (it.id == car.id) car else it })
    }

    /** Löscht das Auto mitsamt seinen Fahrten, Tankbelegen und Terminen; auch das letzte. */
    fun deleteCar(id: String) {
        val current = _cars.value ?: return
        setCars(current.filter { it.id != id })
        _entries.value?.let { list -> setEntries(list.filter { it.carId != id }) }
        _receipts.value?.let { list -> setReceipts(list.filter { it.carId != id }) }
        _cares.value?.let { list -> setCares(list.filter { it.carId != id }) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settings.themeMode = mode
    }

    // --- Termine ---

    fun saveCare(care: CarCare) {
        val current = _cares.value ?: return
        setCares(current.filter { it.carId != care.carId } + care)
    }

    /** Schaltet eine Erinnerung ein oder aus; ihre Angaben bleiben dabei erhalten. */
    fun setCareItemOn(carId: String, item: CareItem, on: Boolean) {
        val care = _cares.value?.find { it.carId == carId } ?: CarCare(carId)
        saveCare(care.withOn(item, on))
    }

    /** Trägt den gebuchten HU-Termin ein; null entfernt ihn. */
    fun setHuAppointment(carId: String, date: LocalDate?) {
        val care = _cares.value?.find { it.carId == carId } ?: return
        saveCare(care.copy(huAppointment = date))
    }

    /** Hakt einen Termin ab und berechnet den nächsten. */
    fun markDone(reminder: Reminder) {
        val current = _cares.value ?: return
        val care = current.find { it.carId == reminder.carId } ?: return
        val now = YearMonth.now()
        val car = _cars.value?.find { it.id == reminder.carId }
        val odometer = car?.let { estimateOdometer(it, _entries.value.orEmpty()) }
        val updated = when (reminder.kind) {
            // Die nächste HU zählt ab dem Prüfmonat; mit gebuchtem Termin ist das dessen Monat.
            ReminderKind.HU -> care.copy(
                huDue = nextHuAfter(care.huAppointment?.let(YearMonth::from) ?: now),
                huAppointment = null,
            )
            ReminderKind.SERVICE -> care.copy(lastService = now, lastServiceOdometer = odometer)
            ReminderKind.WINTER_TIRES, ReminderKind.SUMMER_TIRES -> care.copy(tiresDone = reminder.seasonKey)
            ReminderKind.BRAKE_FLUID -> care.copy(lastBrakeFluid = now)
            ReminderKind.TIMING_BELT -> care.copy(lastBeltOdometer = odometer, lastBeltYear = now.year)
        }
        saveCare(updated)
    }

    // --- Fahrten ---

    /** Legt einen Monat an oder ersetzt ihn. [replacing] ist der ursprüngliche Eintrag beim Bearbeiten. */
    fun saveEntry(entry: Entry, replacing: Entry? = null) {
        val current = _entries.value ?: return
        val updated = current.filter {
            !(it.carId == entry.carId && it.month == entry.month) &&
                !(replacing != null && it.carId == replacing.carId && it.month == replacing.month)
        } + entry
        setEntries(updated.sortedBy { it.month })
    }

    fun deleteEntry(entry: Entry) {
        val current = _entries.value ?: return
        setEntries(current.filter { !(it.carId == entry.carId && it.month == entry.month) })
    }

    // --- Tankbelege ---

    /** Legt einen Beleg an oder ersetzt den mit gleicher id. */
    fun saveReceipt(receipt: FuelReceipt) {
        val current = _receipts.value ?: return
        val updated = current.filter { it.id != receipt.id } + receipt
        setReceipts(updated.sortedWith(FuelRepository.RECEIPT_ORDER))
    }

    fun deleteReceipt(id: String) {
        val current = _receipts.value ?: return
        setReceipts(current.filter { it.id != id })
    }

    // --- CSV ---

    fun exportCsv(uri: Uri) {
        val cars = _cars.value ?: return
        val entries = _entries.value ?: return
        val receipts = _receipts.value ?: return
        viewModelScope.launch(io) {
            try {
                val text = Csv.write(cars, entries, receipts)
                val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Kein Ausgabestrom")
                out.use {
                    it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM für Excel
                    it.write(text.toByteArray())
                }
                _snack.value = "CSV-Datei gespeichert: ${entries.size} Fahrten, ${receipts.size} Tankbelege."
            } catch (e: Exception) {
                _snack.value = "Die Datei konnte nicht gespeichert werden. Bitte einen anderen Speicherort wählen."
            }
        }
    }

    /**
     * Ergänzt die Daten aus einer CSV-Datei. Autos werden über den Namen zugeordnet und bei Bedarf
     * angelegt; ein vorhandener Monat wird ersetzt, ein identischer Tankbeleg übersprungen.
     */
    fun importCsv(uri: Uri) {
        viewModelScope.launch(io) {
            val text = try {
                app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            } catch (e: Exception) {
                null
            }
            if (text == null) {
                _importReport.value = "Die Datei konnte nicht gelesen werden."
                return@launch
            }
            val parsed = try {
                Csv.parse(text)
            } catch (e: Csv.FormatException) {
                _importReport.value = e.message
                return@launch
            }

            var cars = _cars.value ?: return@launch
            val entries = _entries.value?.toMutableList() ?: return@launch
            val receipts = _receipts.value?.toMutableList() ?: return@launch
            val byName = cars.associateBy { it.name.lowercase() }.toMutableMap()
            val newCars = mutableListOf<String>()

            fun carFor(rawName: String): Car {
                val name = rawName.trim().ifBlank { "Mein Auto" }
                return byName.getOrPut(name.lowercase()) {
                    Car(UUID.randomUUID().toString(), name, nextColorIndex(cars)).also {
                        cars = cars + it
                        newCars += name
                    }
                }
            }

            var tripsAdded = 0
            var tripsReplaced = 0
            parsed.trips.forEach { t ->
                val car = carFor(t.car)
                val existing = entries.indexOfFirst { it.carId == car.id && it.month == t.month }
                val entry = Entry(car.id, t.month, t.km, t.l100)
                if (existing >= 0) {
                    entries[existing] = entry
                    tripsReplaced++
                } else {
                    entries += entry
                    tripsAdded++
                }
            }

            var fuelAdded = 0
            var fuelSkipped = 0
            parsed.fuel.forEach { f ->
                val car = carFor(f.car)
                val duplicate = receipts.any {
                    it.carId == car.id && it.date == f.date &&
                        abs(it.liters - f.liters) < 0.005 && abs(it.total - f.total) < 0.005
                }
                if (duplicate) {
                    fuelSkipped++
                } else {
                    receipts += FuelReceipt(UUID.randomUUID().toString(), car.id, f.date, f.liters, f.total)
                    fuelAdded++
                }
            }

            setCars(cars)
            setEntries(entries.sortedBy { it.month })
            setReceipts(receipts.sortedWith(FuelRepository.RECEIPT_ORDER))

            _importReport.value = buildString {
                append("Fahrten: $tripsAdded neu")
                if (tripsReplaced > 0) append(", $tripsReplaced ersetzt")
                append(".\nTankbelege: $fuelAdded neu")
                if (fuelSkipped > 0) append(", $fuelSkipped schon vorhanden und übersprungen")
                append(".")
                if (newCars.isNotEmpty()) append("\nNeu angelegte Autos: ${newCars.joinToString()}.")
                if (parsed.badLines.isNotEmpty()) {
                    val shown = parsed.badLines.take(10).joinToString()
                    val more = if (parsed.badLines.size > 10) " …" else ""
                    append("\n\n${parsed.badLines.size} Zeilen konnten nicht gelesen werden (Zeile $shown$more). ")
                    append("Prüfe dort Typ, Datum bzw. Monat und die Zahlen.")
                }
            }
        }
    }

    fun snackShown() {
        _snack.value = null
    }

    fun importReportShown() {
        _importReport.value = null
    }

    // --- Speichern ---

    private fun setCars(list: List<Car>) {
        _cars.value = list
        persist { carRepo.save(list) }
    }

    private fun setEntries(list: List<Entry>) {
        _entries.value = list
        persist { entryRepo.save(list) }
    }

    private fun setReceipts(list: List<FuelReceipt>) {
        _receipts.value = list
        persist { fuelRepo.save(list) }
    }

    private fun setCares(list: List<CarCare>) {
        _cares.value = list
        persist { careRepo.save(list) }
    }

    private fun persist(block: () -> Unit) {
        viewModelScope.launch(io) {
            try {
                block()
            } catch (e: Exception) {
                _snack.value = "Speichern fehlgeschlagen. Bitte noch einmal versuchen."
            }
        }
    }

    private companion object {
        /** Platzhalter beim Laden alter Daten ohne Auto-Zuordnung. */
        const val NO_CAR = "ohne-auto"
    }
}
