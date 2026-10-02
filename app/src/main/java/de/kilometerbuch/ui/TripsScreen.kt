package de.kilometerbuch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.KM_RANGE
import de.kilometerbuch.data.L100_RANGE
import de.kilometerbuch.data.parseDecimal
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.carColor
import java.time.YearMonth

/** Seite „Fahrten“. [selectedCar] ist null in der Gesamtansicht über alle Autos. */
@Composable
fun TripsContent(
    cars: List<Car>,
    entries: List<Entry>,
    selectedCar: Car?,
    year: Int,
    onYearChange: (Int) -> Unit,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    contentPadding: PaddingValues,
    onEdit: (Entry) -> Unit,
) {
    val shownCars = selectedCar?.let(::listOf) ?: cars
    val shownIds = shownCars.map { it.id }.toSet()
    val shown = entries.filter { it.carId in shownIds }
    val carsById = cars.associateBy { it.id }
    val overview = selectedCar == null && cars.size > 1

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { TripsYearCard(shown, year, onYearChange, allCars = selectedCar == null) }

        if (shown.isEmpty()) {
            item {
                EmptyCard(
                    if (selectedCar != null) "Für „${selectedCar.name}“ ist noch nichts eingetragen" else "Noch keine Einträge",
                    "Tippe unten auf „Monat eintragen“ und gib ein, wie viele Kilometer du gefahren bist " +
                        "und wie hoch der Verbrauch war. Danach siehst du hier den Verlauf als Diagramm.",
                )
            }
        } else {
            val byKey = shown.associateBy { it.carId to it.month }
            val months = monthRange(shown.map { it.month }, range)

            if (overview) {
                item {
                    BreakdownCard(
                        "Pro Auto in $year",
                        cars.map { car ->
                            val st = yearStats(shown.filter { it.carId == car.id }, year)
                            BreakdownRow(
                                color = carColor(car.colorIndex),
                                name = car.name,
                                value = "${fmtInt(st.total.toDouble())} km",
                                detail = st.avgL100?.let { "Ø ${fmt1(it)} l/100 km" } ?: "Kein Verbrauch eingetragen",
                            )
                        },
                    )
                }
            }
            item { RangeSelector(range, onRangeChange) }
            item {
                ChartCard("Kilometer pro Monat", if (overview) "Gestapelt nach Auto" else null) { surface ->
                    MonthChart(
                        months = months,
                        series = shownCars.map { car ->
                            ChartSeries(car.name, carColor(car.colorIndex), months.map { byKey[car.id to it]?.km?.toFloat() })
                        }.filter { s -> s.values.any { it != null } },
                        kind = ChartKind.Bars,
                        unit = "km",
                        format = { fmtInt(it.toDouble()) },
                        surfaceColor = surface,
                        showAverage = true,
                    )
                }
            }
            item {
                ChartCard("Verbrauch", if (overview) "Eine Linie pro Auto" else null) { surface ->
                    val series = shownCars.map { car ->
                        ChartSeries(car.name, carColor(car.colorIndex), months.map { byKey[car.id to it]?.l100?.toFloat() })
                    }.filter { s -> s.values.any { it != null } }
                    if (series.isNotEmpty()) {
                        MonthChart(
                            months = months,
                            series = series,
                            kind = ChartKind.Line,
                            unit = "l/100 km",
                            format = { fmt1(it.toDouble()) },
                            surfaceColor = surface,
                        )
                    } else {
                        Text(
                            "In diesem Zeitraum ist kein Verbrauch eingetragen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text("Einträge", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            val sorted = shown.sortedWith(compareByDescending<Entry> { it.month }.thenBy { carsById[it.carId]?.name })
            items(sorted, key = { "${it.carId}/${it.month}" }) { entry ->
                EntryRow(entry, if (overview) carsById[entry.carId] else null) { onEdit(entry) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TripsYearCard(entries: List<Entry>, year: Int, onYearChange: (Int) -> Unit, allCars: Boolean) {
    val years = entries.map { it.month.year } + YearMonth.now().year
    val stats = yearStats(entries, year)

    YearCard {
        YearHeader(if (allCars) "Gefahren, alle Autos" else "Gefahren", year, years, onYearChange)
        Column {
            Row {
                Text(
                    fmtInt(stats.total.toDouble()),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(8.dp))
                Text("km", style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
            }
            Text(
                when (stats.months) {
                    0 -> "In $year noch kein Monat eingetragen"
                    1 -> "aus 1 Monat"
                    else -> "aus ${stats.months} Monaten"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("Ø pro Monat", stats.avgKm?.let(::fmtInt), "km", Modifier.weight(1f))
            Stat("Ø Verbrauch", stats.avgL100?.let(::fmt1), "l/100 km", Modifier.weight(1f))
            Stat("Sprit ca.", stats.liters?.let(::fmtInt), "Liter", Modifier.weight(1f))
        }
    }
}

/** [car] wird nur in der Gesamtansicht übergeben und dann mit angezeigt. */
@Composable
private fun EntryRow(entry: Entry, car: Car?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (car != null) {
            ColorDot(carColor(car.colorIndex))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(monthLong(entry.month), style = MaterialTheme.typography.bodyLarge)
            val consumption = entry.l100?.let { "${fmt1(it)} l/100 km" } ?: "Kein Verbrauch eingetragen"
            Text(
                if (car != null) "${car.name} · $consumption" else consumption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${fmtInt(entry.km.toDouble())} km",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun EntryDialog(
    original: Entry?,
    cars: List<Car>,
    initialCarId: String,
    existing: List<Entry>,
    onDismiss: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: () -> Unit,
) {
    var carId by remember { mutableStateOf(original?.carId ?: initialCarId) }
    var month by remember { mutableStateOf(original?.month ?: suggestMonth(existing.filter { it.carId == carId })) }
    var kmText by remember { mutableStateOf(original?.km?.toString() ?: "") }
    var l100Text by remember { mutableStateOf(original?.l100?.let(::decimalInput) ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val km = parseKm(kmText)
    val l100 = parseDecimal(l100Text)
    val kmError = when {
        kmText.isBlank() -> "Bitte die gefahrenen Kilometer eintragen."
        km == null || km !in KM_RANGE -> "Bitte eine ganze Zahl zwischen 0 und 100.000 eingeben."
        else -> null
    }
    val l100Error = if (l100Text.isNotBlank() && (l100 == null || l100 !in L100_RANGE)) {
        "Bitte einen Wert zwischen 0,5 und 50 eingeben, z. B. 6,4."
    } else {
        null
    }
    val isOriginalSlot = original != null && original.carId == carId && original.month == month
    val overwrites = !isOriginalSlot && existing.any { it.carId == carId && it.month == month }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Monat eintragen" else "Eintrag bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPicker(cars, carId) { carId = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriger Monat")
                    }
                    Text(
                        monthLong(month),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Nächster Monat")
                    }
                }
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it },
                    label = { Text("Gefahren") },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = showErrors && kmError != null,
                    supportingText = if (showErrors && kmError != null) {
                        { Text(kmError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = l100Text,
                    onValueChange = { l100Text = it },
                    label = { Text("Verbrauch (optional)") },
                    suffix = { Text("l/100 km") },
                    singleLine = true,
                    isError = showErrors && l100Error != null,
                    supportingText = if (showErrors && l100Error != null) {
                        { Text(l100Error) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overwrites) {
                    Text(
                        "Für ${monthLong(month)} gibt es schon einen Eintrag. Er wird ersetzt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (kmError == null && l100Error == null && km != null) {
                    onSave(Entry(carId, month, km, if (l100Text.isBlank()) null else l100))
                } else {
                    showErrors = true
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(onClick = onDelete) {
                        Text("Löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

// --- Vorschau in Android Studio (Split/Design-Ansicht), nur mit Beispieldaten ---

internal val previewCars = listOf(
    Car("golf", "Golf", 0),
    Car("firma", "Firmenwagen", 1),
)

private val previewEntries = listOf(
    1180 to 6.9, 960 to 7.2, 1340 to 6.8, 1420 to 6.5, 1510 to 6.3, 1890 to 6.1,
    2240 to 5.9, 1760 to 6.0, 1390 to 6.4, 1210 to 6.7, 1050 to 7.0, 1460 to 6.6,
).flatMapIndexed { i, (km, l100) ->
    val month = YearMonth.of(2025, 10).plusMonths(i.toLong())
    listOf(
        Entry("golf", month, km, l100),
        Entry("firma", month, (km * 0.7).toInt(), l100 + 1.1),
    )
}

@Preview(name = "Fahrten, ein Auto", showBackground = true, heightDp = 1500)
@Composable
private fun TripsPreview() {
    KilometerTheme {
        Surface {
            TripsContent(previewCars, previewEntries, previewCars[0], 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {})
        }
    }
}

@Preview(
    name = "Fahrten, alle Autos (dunkel)",
    showBackground = true,
    heightDp = 1900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TripsAllPreview() {
    KilometerTheme {
        Surface {
            TripsContent(previewCars, previewEntries, null, 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {})
        }
    }
}

@Preview(name = "Monat eintragen")
@Composable
private fun EntryDialogPreview() {
    KilometerTheme {
        EntryDialog(
            original = null, cars = previewCars, initialCarId = "golf", existing = previewEntries,
            onDismiss = {}, onSave = {}, onDelete = {},
        )
    }
}
