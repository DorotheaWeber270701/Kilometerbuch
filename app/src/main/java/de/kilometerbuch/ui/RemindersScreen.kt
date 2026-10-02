package de.kilometerbuch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.ODOMETER_RANGE
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.LocalDarkTheme
import de.kilometerbuch.ui.theme.carColor
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/**
 * Seite „Termine“: pro Auto alle Erinnerungen als Karten mit Schalter. Eingeschaltete zeigen,
 * wann sie fällig sind; ausgeschaltete, was sie tun und welche Angaben sie brauchen.
 * [selectedCar] ist null in der Gesamtansicht über alle Autos.
 */
@Composable
fun RemindersContent(
    cars: List<Car>,
    cares: List<CarCare>,
    entries: List<Entry>,
    selectedCar: Car?,
    contentPadding: PaddingValues,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
    onToggle: (Car, CareItem, Boolean) -> Unit,
    onEdit: (Car, CareItem) -> Unit,
    onDone: (Reminder) -> Unit,
    onSetHuAppointment: (carId: String, date: LocalDate?) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val shownCars = selectedCar?.let(::listOf) ?: cars
    val caresById = cares.associateBy { it.carId }

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (!notificationsAllowed && shownCars.any { caresById[it.id]?.anyOn == true }) {
            item(key = "benachrichtigungen") { NotificationHint(onAllowNotifications) }
        }

        shownCars.forEach { car ->
            val care = caresById[car.id] ?: CarCare(car.id)
            val reminderByItem = reminders(car, care, entries, today).associateBy { it.kind.item }
            val onItems = CareItem.entries.filter { care.isOn(it) }.sortedBy { reminderByItem[it]?.due ?: LocalDate.MAX }
            val offItems = CareItem.entries.filter { !care.isOn(it) }

            item(key = "kopf-${car.id}") { CarHeader(car, estimateOdometer(car, entries)) }
            if (!care.anyOn) {
                item(key = "intro-${car.id}") { IntroCard() }
            }
            onItems.forEach { careItem ->
                item(key = "${car.id}-${careItem.name}") {
                    CareItemCard(
                        item = careItem,
                        on = true,
                        reminder = reminderByItem[careItem],
                        onToggle = { onToggle(car, careItem, it) },
                        onEdit = { onEdit(car, careItem) },
                        onDone = onDone,
                        onSetAppointment = { date -> onSetHuAppointment(car.id, date) },
                    )
                }
            }
            if (onItems.isNotEmpty() && offItems.isNotEmpty()) {
                item(key = "weitere-${car.id}") {
                    Text(
                        "Weitere Erinnerungen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            offItems.forEach { careItem ->
                item(key = "${car.id}-${careItem.name}") {
                    CareItemCard(
                        item = careItem,
                        on = false,
                        reminder = null,
                        onToggle = { onToggle(car, careItem, it) },
                        onEdit = { onEdit(car, careItem) },
                        onDone = onDone,
                        onSetAppointment = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun CarHeader(car: Car, odometer: Int?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(carColor(car.colorIndex))
        Spacer(Modifier.width(10.dp))
        Text(car.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val facts = listOfNotNull(
            odometer?.let { "ca. ${fmtInt(it.toDouble())} km" },
            car.buildYear?.let { "Baujahr $it" },
        )
        if (facts.isNotEmpty()) {
            Text(
                facts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Woran soll die App dich erinnern?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Schalte unten ein, was du brauchst. Die App fragt dann nur nach den Angaben für diese " +
                    "Erinnerung und rechnet den Rest selbst aus, zum Beispiel aus deinen Monatskilometern. " +
                    "Ist ein Termin bald fällig, bekommst du eine Benachrichtigung.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NotificationHint(onAllow: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Benachrichtigungen sind aus", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ohne Benachrichtigungen siehst du fällige Termine nur hier in der App.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onAllow) { Text("Benachrichtigungen erlauben") }
        }
    }
}

/** Farben für den Status: Rot = überfällig, Gelb = bald, neutral = später. */
@Composable
private fun urgencyColors(urgency: Urgency): Pair<Color, Color> {
    val dark = LocalDarkTheme.current
    return when (urgency) {
        Urgency.OVERDUE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Urgency.SOON -> if (dark) Color(0xFF5C4200) to Color(0xFFFFDDA0) else Color(0xFFFFE8B3) to Color(0xFF5C3B00)
        Urgency.LATER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun UrgencyPill(urgency: Urgency) {
    val (bg, fg) = urgencyColors(urgency)
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Text(
            when (urgency) {
                Urgency.OVERDUE -> "Überfällig"
                Urgency.SOON -> "Bald fällig"
                Urgency.LATER -> "Später"
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Eine Erinnerung mit Schalter. Eingeschaltet mit Fälligkeit und Aktionen, ausgeschaltet mit Erklärung. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CareItemCard(
    item: CareItem,
    on: Boolean,
    reminder: Reminder?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDone: (Reminder) -> Unit,
    onSetAppointment: (LocalDate?) -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    var editingAppointment by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (on) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    reminder?.kind?.title ?: item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = on, onCheckedChange = onToggle)
            }

            when {
                on && reminder != null -> {
                    UrgencyPill(reminder.urgency)
                    Text(reminder.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(reminder.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (item == CareItem.HU) {
                            TextButton(onClick = { editingAppointment = true }) {
                                Text(if (reminder.appointment == null) "Termin eintragen" else "Termin ändern")
                            }
                        }
                        if (item != CareItem.TIRES) {
                            TextButton(onClick = onEdit) { Text("Angaben ändern") }
                        }
                        TextButton(onClick = { confirming = true }) { Text("Erledigt") }
                    }
                }

                on -> {
                    Text(
                        "Es fehlen noch Angaben, damit die App den Termin berechnen kann.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onEdit) { Text("Angaben ergänzen") }
                }

                else -> {
                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Benötigt", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    if (item.needs.isEmpty()) {
                        Text("Nichts, läuft von selbst.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        item.needs.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    if (confirming && reminder != null) {
        val now = YearMonth.now()
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("${reminder.kind.title} erledigt?") },
            text = {
                Text(
                    when (reminder.kind) {
                        ReminderKind.HU -> {
                            val inspected = reminder.appointment?.let(YearMonth::from) ?: now
                            "Die nächste HU wird auf ${monthLong(nextHuAfter(inspected))} gesetzt."
                        }
                        ReminderKind.SERVICE ->
                            "Die letzte Inspektion wird auf ${monthLong(now)} gesetzt, beim heutigen Kilometerstand."
                        ReminderKind.WINTER_TIRES, ReminderKind.SUMMER_TIRES ->
                            "Die Erinnerung für diese Saison wird ausgeblendet. Zur nächsten Saison kommt sie wieder."
                        ReminderKind.BRAKE_FLUID -> "Der letzte Wechsel wird auf ${monthLong(now)} gesetzt."
                        ReminderKind.TIMING_BELT ->
                            "Der letzte Wechsel wird auf ${now.year} beim heutigen Kilometerstand gesetzt."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDone(reminder)
                }) { Text("Erledigt") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Abbrechen") } },
        )
    }

    if (editingAppointment && reminder != null) {
        AppointmentDialog(
            initial = reminder.appointment,
            onDismiss = { editingAppointment = false },
            onSave = {
                editingAppointment = false
                onSetAppointment(it)
            },
        )
    }
}

/** Gebuchten TÜV-Termin eintragen; „Entfernen“ löscht einen eingetragenen Termin. */
@Composable
private fun AppointmentDialog(initial: LocalDate?, onDismiss: () -> Unit, onSave: (LocalDate?) -> Unit) {
    var date by remember { mutableStateOf(initial) }
    var showErrors by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TÜV-Termin") },
        text = {
            DateField(
                value = date,
                onValueChange = { date = it },
                label = "Datum des Termins",
                showErrors = showErrors,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { date?.let(onSave) ?: run { showErrors = true } }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

/** Monat mit Pfeilen wählen. */
@Composable
fun MonthStepper(month: YearMonth, onChange: (YearMonth) -> Unit, max: YearMonth? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Monat")
        }
        Text(
            monthLong(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = max == null || month < max) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
        }
    }
}

private data class ServicePreset(val label: String, val months: Int, val km: Int?)

private val SERVICE_PRESETS = listOf(
    ServicePreset("1 Jahr · 15.000 km", 12, 15_000),
    ServicePreset("2 Jahre · 30.000 km", 24, 30_000),
)

private val BRAKE_OPTIONS = listOf(12 to "1 Jahr", 24 to "2 Jahre", 36 to "3 Jahre")

/** Zahlenfeld mit Einheit; Fehlertext oder Hinweis erscheint direkt darunter. */
@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String?,
    error: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
}

/**
 * Fragt genau die Angaben ab, die [item] braucht, und schaltet es beim Speichern ein.
 * Fehlen dem Auto Kilometerstand oder Baujahr, werden sie hier gleich mit abgefragt.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareItemDialog(
    car: Car,
    care: CarCare,
    item: CareItem,
    onDismiss: () -> Unit,
    onSave: (CarCare, Car) -> Unit,
) {
    val now = YearMonth.now()
    val thisYear = Year.now().value
    var showErrors by remember { mutableStateOf(false) }

    // HU
    var huDue by remember { mutableStateOf(care.huDue ?: now.plusMonths(12)) }
    var huWarnDays by remember { mutableStateOf(care.huWarnDays) }

    // Inspektion
    var lastService by remember { mutableStateOf(care.lastService ?: now.minusMonths(6)) }
    var serviceOdoText by remember { mutableStateOf(care.lastServiceOdometer?.toString() ?: "") }
    val initialPreset = SERVICE_PRESETS.indexOfFirst { it.months == care.serviceMonths && it.km == care.serviceKm }
    var preset by remember { mutableStateOf(initialPreset) } // -1 = eigenes Intervall
    var monthsText by remember { mutableStateOf(care.serviceMonths.toString()) }
    var kmText by remember { mutableStateOf(care.serviceKm?.toString() ?: "") }

    // Bremsflüssigkeit
    var lastBrake by remember { mutableStateOf(care.lastBrakeFluid ?: now.minusMonths(12)) }
    var brakeMonths by remember { mutableStateOf(care.brakeMonths) }

    // Zahnriemen
    var beltKmText by remember { mutableStateOf(care.beltKm.toString()) }
    var beltYearsText by remember { mutableStateOf(care.beltYears.toString()) }
    var beltChanged by remember { mutableStateOf(care.lastBeltYear != null || care.lastBeltOdometer != null) }
    var beltYearText by remember { mutableStateOf(care.lastBeltYear?.toString() ?: "") }
    var beltOdoText by remember { mutableStateOf(care.lastBeltOdometer?.toString() ?: "") }

    // Fehlende Angaben zum Auto
    var carOdoText by remember { mutableStateOf("") }
    var buildYearText by remember { mutableStateOf("") }

    fun optionalKm(text: String) = if (text.isBlank()) null else parseKm(text)
    fun kmError(text: String) = if (text.isNotBlank() && optionalKm(text)?.let { it in ODOMETER_RANGE } != true) {
        "Bitte eine ganze Zahl eingeben, z. B. 48.300."
    } else {
        null
    }
    fun yearError(text: String, required: Boolean) = when {
        text.isBlank() -> if (required) "Bitte das Jahr eingeben." else null
        text.trim().toIntOrNull()?.let { it in 1950..thisYear } != true -> "Bitte ein Jahr zwischen 1950 und $thisYear eingeben."
        else -> null
    }

    val askCarOdometer = car.odometerKm == null && (item == CareItem.SERVICE || item == CareItem.TIMING_BELT)
    val carOdo = optionalKm(carOdoText)
    val carOdoError = if (askCarOdometer) kmError(carOdoText) else null

    // Ein km-Stand bei der letzten Inspektion nützt nur mit heutigem Kilometerstand.
    val serviceOdo = optionalKm(serviceOdoText)
    val serviceOdoError = kmError(serviceOdoText)
        ?: if (serviceOdo != null && car.odometerKm == null && carOdo == null) "Dafür bitte auch den heutigen Kilometerstand eintragen." else null
    val customMonths = monthsText.trim().toIntOrNull()
    val customKm = if (kmText.isBlank()) null else parseKm(kmText)
    val monthsError = if (preset < 0 && (customMonths == null || customMonths !in 1..60)) "1 bis 60 Monate" else null
    val serviceKmError = if (preset < 0 && kmText.isNotBlank() && (customKm == null || customKm !in 1_000..100_000)) {
        "1.000 bis 100.000 km oder leer"
    } else {
        null
    }

    val beltKm = parseKm(beltKmText)
    val beltKmError = if (beltKm == null || beltKm !in 10_000..400_000) "10.000 bis 400.000 km" else null
    val beltYears = beltYearsText.trim().toIntOrNull()
    val beltYearsError = if (beltYears == null || beltYears !in 1..20) "1 bis 20 Jahre" else null
    val askBuildYear = item == CareItem.TIMING_BELT && !beltChanged && car.buildYear == null
    val buildYearError = if (askBuildYear) yearError(buildYearText, required = true) else null
    val beltYearError = if (item == CareItem.TIMING_BELT && beltChanged) yearError(beltYearText, required = true) else null
    val beltOdoError = if (beltChanged) kmError(beltOdoText) else null

    val errors = when (item) {
        CareItem.HU, CareItem.TIRES, CareItem.BRAKE_FLUID -> emptyList()
        CareItem.SERVICE -> listOf(carOdoError, serviceOdoError, monthsError, serviceKmError)
        CareItem.TIMING_BELT -> listOf(carOdoError, beltKmError, beltYearsError, buildYearError, beltYearError, beltOdoError)
    }.filterNotNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item) {
                    CareItem.HU -> {
                        Label("Nächste HU fällig im")
                        MonthStepper(huDue, { huDue = it })
                        Hint("Steht auf der Plakette am hinteren Kennzeichen und im Fahrzeugschein.")
                        Label("Erinnern vor dem Plaketten-Monat")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HU_WARN_OPTIONS.forEach { (days, label) ->
                                FilterChip(selected = huWarnDays == days, onClick = { huWarnDays = days }, label = { Text(label) })
                            }
                        }
                        Hint("Empfehlung: 1 Monat. Früher zur HU zu gehen verschenkt Zeit, denn die nächsten 2 Jahre zählen ab dem Prüfmonat.")
                    }

                    CareItem.SERVICE -> {
                        Label("Letzte Inspektion")
                        MonthStepper(lastService, { lastService = it }, max = now)
                        NumberField(
                            serviceOdoText, { serviceOdoText = it }, "km-Stand dabei (optional)", "km",
                            if (showErrors) serviceOdoError else kmError(serviceOdoText),
                            hint = "Steht im Serviceheft. Damit wird genauer gerechnet.",
                        )
                        if (askCarOdometer) {
                            NumberField(
                                carOdoText, { carOdoText = it }, "Heutiger Kilometerstand (optional)", "km", carOdoError,
                                hint = "Wird beim Auto gespeichert und mit deinen Monatskilometern fortgeschrieben.",
                            )
                        }
                        Label("Intervall laut Serviceheft")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SERVICE_PRESETS.forEachIndexed { i, p ->
                                FilterChip(selected = preset == i, onClick = { preset = i }, label = { Text(p.label) })
                            }
                            FilterChip(selected = preset < 0, onClick = { preset = -1 }, label = { Text("Eigenes Intervall") })
                        }
                        if (preset < 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(monthsText, { monthsText = it }, "Alle", "Monate", if (showErrors) monthsError else null, Modifier.weight(1f))
                                NumberField(kmText, { kmText = it }, "oder alle", "km", serviceKmError, Modifier.weight(1f))
                            }
                        }
                        Hint("Es zählt, was zuerst erreicht ist.")
                    }

                    CareItem.BRAKE_FLUID -> {
                        Label("Letzter Wechsel")
                        MonthStepper(lastBrake, { lastBrake = it }, max = now)
                        Hint("Steht im Serviceheft oder auf der Rechnung der Werkstatt.")
                        Label("Wechseln alle")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BRAKE_OPTIONS.forEach { (months, label) ->
                                FilterChip(selected = brakeMonths == months, onClick = { brakeMonths = months }, label = { Text(label) })
                            }
                        }
                    }

                    CareItem.TIMING_BELT -> {
                        Label("Intervall laut Serviceheft")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(beltKmText, { beltKmText = it }, "Alle", "km", beltKmError, Modifier.weight(1f))
                            NumberField(beltYearsText, { beltYearsText = it }, "oder alle", "Jahre", beltYearsError, Modifier.weight(1f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Schon einmal gewechselt", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = beltChanged, onCheckedChange = { beltChanged = it })
                        }
                        if (beltChanged) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    beltYearText, { beltYearText = it.filter(Char::isDigit).take(4) }, "Im Jahr", null,
                                    if (showErrors) beltYearError else null, Modifier.weight(1f),
                                )
                                NumberField(beltOdoText, { beltOdoText = it }, "bei (optional)", "km", beltOdoError, Modifier.weight(1f))
                            }
                        } else if (askBuildYear) {
                            NumberField(
                                buildYearText, { buildYearText = it.filter(Char::isDigit).take(4) }, "Baujahr", null,
                                if (showErrors) buildYearError else null,
                                hint = "Ohne Wechsel zählt die Zeit ab Baujahr. Wird beim Auto gespeichert.",
                            )
                        }
                        if (askCarOdometer) {
                            NumberField(
                                carOdoText, { carOdoText = it }, "Heutiger Kilometerstand (optional)", "km", carOdoError,
                                hint = "Ohne Kilometerstand wird nur nach Jahren erinnert.",
                            )
                        }
                    }

                    CareItem.TIRES -> Hint(item.description)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (errors.isNotEmpty()) {
                    showErrors = true
                    return@TextButton
                }
                val updatedCar = car.copy(
                    odometerKm = carOdo ?: car.odometerKm,
                    odometerMonth = if (carOdo != null) now else car.odometerMonth,
                    buildYear = if (askBuildYear) buildYearText.trim().toInt() else car.buildYear,
                )
                val updatedCare = when (item) {
                    CareItem.HU -> care.copy(
                        huDue = huDue,
                        huWarnDays = huWarnDays,
                        // Ein gebuchter Termin passt nur zum bisherigen Plaketten-Monat.
                        huAppointment = if (huDue == care.huDue) care.huAppointment else null,
                    )
                    CareItem.SERVICE -> {
                        val (months, km) = if (preset >= 0) {
                            SERVICE_PRESETS[preset].months to SERVICE_PRESETS[preset].km
                        } else {
                            customMonths!! to customKm
                        }
                        care.copy(lastService = lastService, lastServiceOdometer = serviceOdo, serviceMonths = months, serviceKm = km)
                    }
                    CareItem.BRAKE_FLUID -> care.copy(lastBrakeFluid = lastBrake, brakeMonths = brakeMonths)
                    CareItem.TIMING_BELT -> care.copy(
                        beltKm = beltKm!!,
                        beltYears = beltYears!!,
                        lastBeltYear = if (beltChanged) beltYearText.trim().toInt() else null,
                        lastBeltOdometer = if (beltChanged) optionalKm(beltOdoText) else null,
                    )
                    CareItem.TIRES -> care
                }
                onSave(updatedCare.withOn(item, true), updatedCar)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// --- Vorschau in Android Studio (Split/Design-Ansicht), nur mit Beispieldaten ---

private val previewToday = LocalDate.of(2026, 10, 2)

private val previewReminderCars = listOf(
    Car("golf", "Golf", 0, odometerKm = 46_500, odometerMonth = YearMonth.of(2026, 6), buildYear = 2019),
    Car("firma", "Firmenwagen", 1),
)

private val previewCares = listOf(
    CarCare(
        "golf",
        huOn = true, huDue = YearMonth.of(2026, 10),
        serviceOn = true, lastService = YearMonth.of(2025, 12), lastServiceOdometer = 38_000,
        tiresOn = true,
    ),
)

private val previewTripEntries = (0 until 9).map { i ->
    Entry("golf", YearMonth.of(2026, 1).plusMonths(i.toLong()), 1400, 6.5)
}

@Preview(name = "Termine, alle Autos", showBackground = true, heightDp = 2200)
@Composable
private fun RemindersPreview() {
    KilometerTheme {
        Surface {
            RemindersContent(
                cars = previewReminderCars, cares = previewCares, entries = previewTripEntries, selectedCar = null,
                contentPadding = PaddingValues(16.dp), notificationsAllowed = true, onAllowNotifications = {},
                onToggle = { _, _, _ -> }, onEdit = { _, _ -> }, onDone = {}, onSetHuAppointment = { _, _ -> },
                today = previewToday,
            )
        }
    }
}

@Preview(name = "Zahnriemen einrichten")
@Composable
private fun CareItemDialogPreview() {
    KilometerTheme {
        CareItemDialog(
            car = previewReminderCars[1], care = CarCare("firma"), item = CareItem.TIMING_BELT,
            onDismiss = {}, onSave = { _, _ -> },
        )
    }
}
