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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.ODOMETER_RANGE
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.i18n.asString
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.LocalDarkTheme
import de.kilometerbuch.ui.theme.carColor
import java.time.LocalDate
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
                        stringResource(R.string.more_reminders),
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
        ColorDot(carColor(car))
        Spacer(Modifier.width(10.dp))
        Text(car.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val facts = listOfNotNull(
            odometer?.let { stringResource(R.string.odo_fact, fmtInt(it.toDouble())) },
            car.buildYear?.let { stringResource(R.string.build_year_fact, it) },
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
            Text(stringResource(R.string.intro_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.intro_text), style = MaterialTheme.typography.bodyMedium)
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
            Text(stringResource(R.string.notif_off_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.notif_off_text), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAllow) { Text(stringResource(R.string.notif_allow)) }
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
            stringResource(
                when (urgency) {
                    Urgency.OVERDUE -> R.string.urgency_overdue
                    Urgency.SOON -> R.string.urgency_soon
                    Urgency.LATER -> R.string.urgency_later
                },
            ),
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
                    stringResource(reminder?.kind?.title ?: item.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = on, onCheckedChange = onToggle)
            }

            when {
                on && reminder != null -> {
                    UrgencyPill(reminder.urgency)
                    Text(reminder.headline.asString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(reminder.detail.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (item == CareItem.HU) {
                            TextButton(onClick = { editingAppointment = true }) {
                                Text(stringResource(if (reminder.appointment == null) R.string.appt_add else R.string.appt_change))
                            }
                        }
                        if (item != CareItem.TIRES) {
                            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_details)) }
                        }
                        TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.done)) }
                    }
                }

                on -> {
                    Text(
                        stringResource(R.string.missing_details),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.complete_details)) }
                }

                else -> {
                    Text(stringResource(item.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.needs_label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    if (item.needs.isEmpty()) {
                        Text(stringResource(R.string.needs_nothing), style = MaterialTheme.typography.bodySmall)
                    } else {
                        item.needs.forEach { Text("•  ${stringResource(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }

    if (confirming && reminder != null) {
        val now = YearMonth.now()
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.done_title, stringResource(reminder.kind.title))) },
            text = {
                Text(
                    when (reminder.kind) {
                        ReminderKind.HU -> {
                            val inspected = reminder.appointment?.let(YearMonth::from) ?: now
                            stringResource(R.string.done_hu, monthLong(nextHuAfter(inspected)))
                        }
                        ReminderKind.SERVICE -> stringResource(R.string.done_service, monthLong(now))
                        ReminderKind.WINTER_TIRES, ReminderKind.SUMMER_TIRES -> stringResource(R.string.done_tires)
                        ReminderKind.BRAKE_FLUID -> stringResource(R.string.done_brake, monthLong(now))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onDone(reminder)
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.appt_title)) },
        text = {
            DateField(
                value = date,
                onValueChange = { date = it },
                label = stringResource(R.string.appt_date),
                showErrors = showErrors,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { date?.let(onSave) ?: run { showErrors = true } }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Monat mit Pfeilen wählen. */
@Composable
fun MonthStepper(month: YearMonth, onChange: (YearMonth) -> Unit, max: YearMonth? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_month))
        }
        Text(
            monthLong(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = max == null || month < max) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
        }
    }
}

private data class ServicePreset(val label: Int, val months: Int, val km: Int?)

private val SERVICE_PRESETS = listOf(
    ServicePreset(R.string.preset_1y, 12, 15_000),
    ServicePreset(R.string.preset_2y, 24, 30_000),
)

private val BRAKE_OPTIONS = listOf(12 to R.string.brake_1y, 24 to R.string.brake_2y, 36 to R.string.brake_3y)

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
 * Fehlt dem Auto der Kilometerstand, wird er bei der Inspektion gleich mit abgefragt.
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

    // Fehlender Kilometerstand des Autos
    var carOdoText by remember { mutableStateOf("") }

    fun optionalKm(text: String) = if (text.isBlank()) null else parseKm(text)
    fun kmInvalid(text: String) = text.isNotBlank() && optionalKm(text)?.let { it in ODOMETER_RANGE } != true

    val askCarOdometer = car.odometerKm == null && item == CareItem.SERVICE
    val carOdo = optionalKm(carOdoText)
    val carOdoError = if (askCarOdometer && kmInvalid(carOdoText)) stringResource(R.string.err_whole_number) else null

    // Ein km-Stand bei der letzten Inspektion nützt nur mit heutigem Kilometerstand.
    val serviceOdo = optionalKm(serviceOdoText)
    val serviceOdoError = when {
        kmInvalid(serviceOdoText) -> stringResource(R.string.err_whole_number)
        serviceOdo != null && car.odometerKm == null && carOdo == null -> stringResource(R.string.err_service_needs_odo)
        else -> null
    }
    val customMonths = monthsText.trim().toIntOrNull()
    val customKm = if (kmText.isBlank()) null else parseKm(kmText)
    val monthsError = if (preset < 0 && (customMonths == null || customMonths !in 1..60)) stringResource(R.string.err_months_range) else null
    val serviceKmError = if (preset < 0 && kmText.isNotBlank() && (customKm == null || customKm !in 1_000..100_000)) {
        stringResource(R.string.err_service_km_range)
    } else {
        null
    }

    val hasErrors = item == CareItem.SERVICE && listOf(carOdoError, serviceOdoError, monthsError, serviceKmError).any { it != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(item.title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (item) {
                    CareItem.HU -> {
                        Label(stringResource(R.string.hu_next_due))
                        MonthStepper(huDue, { huDue = it })
                        Hint(stringResource(R.string.hu_plakette_hint))
                        Label(stringResource(R.string.hu_warn_label))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HU_WARN_OPTIONS.forEach { (days, label) ->
                                FilterChip(selected = huWarnDays == days, onClick = { huWarnDays = days }, label = { Text(stringResource(label)) })
                            }
                        }
                        Hint(stringResource(R.string.hu_warn_reco))
                    }

                    CareItem.SERVICE -> {
                        Label(stringResource(R.string.service_last_label))
                        MonthStepper(lastService, { lastService = it }, max = now)
                        NumberField(
                            serviceOdoText, { serviceOdoText = it }, stringResource(R.string.service_odo_label), "km",
                            if (showErrors) serviceOdoError else if (kmInvalid(serviceOdoText)) serviceOdoError else null,
                            hint = stringResource(R.string.service_odo_hint),
                        )
                        if (askCarOdometer) {
                            NumberField(
                                carOdoText, { carOdoText = it }, stringResource(R.string.current_odo_optional), "km", carOdoError,
                                hint = stringResource(R.string.current_odo_hint),
                            )
                        }
                        Label(stringResource(R.string.interval_label))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SERVICE_PRESETS.forEachIndexed { i, p ->
                                FilterChip(selected = preset == i, onClick = { preset = i }, label = { Text(stringResource(p.label)) })
                            }
                            FilterChip(
                                selected = preset < 0,
                                onClick = { preset = -1 },
                                label = { Text(stringResource(R.string.custom_interval)) },
                            )
                        }
                        if (preset < 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberField(
                                    monthsText, { monthsText = it }, stringResource(R.string.every), stringResource(R.string.unit_months),
                                    if (showErrors) monthsError else null, Modifier.weight(1f),
                                )
                                NumberField(kmText, { kmText = it }, stringResource(R.string.or_every), "km", serviceKmError, Modifier.weight(1f))
                            }
                        }
                        Hint(stringResource(R.string.first_reached))
                    }

                    CareItem.BRAKE_FLUID -> {
                        Label(stringResource(R.string.brake_last_label))
                        MonthStepper(lastBrake, { lastBrake = it }, max = now)
                        Hint(stringResource(R.string.brake_hint))
                        Label(stringResource(R.string.brake_every))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BRAKE_OPTIONS.forEach { (months, label) ->
                                FilterChip(selected = brakeMonths == months, onClick = { brakeMonths = months }, label = { Text(stringResource(label)) })
                            }
                        }
                    }

                    CareItem.TIRES -> Hint(stringResource(item.description))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (hasErrors) {
                    showErrors = true
                    return@TextButton
                }
                val updatedCar = if (carOdo != null) car.copy(odometerKm = carOdo, odometerMonth = now) else car
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
                    CareItem.TIRES -> care
                }
                onSave(updatedCare.withOn(item, true), updatedCar)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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

@Preview(name = "Termine, alle Autos", showBackground = true, heightDp = 2000)
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

@Preview(name = "Inspektion einrichten")
@Composable
private fun CareItemDialogPreview() {
    KilometerTheme {
        CareItemDialog(car = previewReminderCars[1], care = CarCare("firma"), item = CareItem.SERVICE, onDismiss = {}, onSave = { _, _ -> })
    }
}
