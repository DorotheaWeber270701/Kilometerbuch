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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.KM_RANGE
import de.kilometerbuch.data.L100_RANGE
import de.kilometerbuch.data.ODOMETER_RANGE
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.data.parseDecimal
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.carColor
import java.time.YearMonth
import kotlin.math.abs

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
    onAdjustOdometer: (Car) -> Unit,
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
        if (selectedCar != null) {
            item { OdometerRow(estimateOdometer(selectedCar, entries)) { onAdjustOdometer(selectedCar) } }
        }

        if (shown.isEmpty()) {
            item {
                EmptyCard(
                    if (selectedCar != null) {
                        stringResource(R.string.trips_empty_title_car, selectedCar.name)
                    } else {
                        stringResource(R.string.trips_empty_title)
                    },
                    stringResource(R.string.trips_empty_text),
                )
            }
        } else {
            val byKey = shown.associateBy { it.carId to it.month }
            val months = monthRange(shown.map { it.month }, range)

            if (overview) {
                item {
                    BreakdownCard(
                        stringResource(R.string.per_car_in_year, year),
                        cars.map { car ->
                            val st = yearStats(shown.filter { it.carId == car.id }, year)
                            BreakdownRow(
                                color = carColor(car),
                                name = car.name,
                                value = "${fmtInt(st.total.toDouble())} km",
                                detail = st.avgL100?.let { stringResource(R.string.avg_consumption_value, fmt1(it)) }
                                    ?: stringResource(R.string.no_consumption),
                            )
                        },
                    )
                }
            }
            item { RangeSelector(range, onRangeChange) }
            item {
                ChartCard(
                    stringResource(R.string.chart_km),
                    if (overview) stringResource(R.string.stacked_by_car) else null,
                ) { surface ->
                    MonthChart(
                        months = months,
                        series = shownCars.map { car ->
                            ChartSeries(car.name, carColor(car), months.map { byKey[car.id to it]?.km?.toFloat() })
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
                ChartCard(
                    stringResource(R.string.chart_consumption),
                    if (overview) stringResource(R.string.one_line_per_car) else null,
                ) { surface ->
                    val series = shownCars.map { car ->
                        ChartSeries(car.name, carColor(car), months.map { byKey[car.id to it]?.l100?.toFloat() })
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
                            stringResource(R.string.no_consumption_range),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.entries_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val sorted = shown.sortedWith(compareByDescending<Entry> { it.month }.thenBy { carsById[it.carId]?.name })
            items(sorted, key = { "${it.carId}/${it.month}" }) { entry ->
                EntryRow(entry, if (overview) carsById[entry.carId] else null) { onEdit(entry) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Kleine Zeile mit dem geschätzten Tachostand und der Möglichkeit, ihn abzugleichen. */
@Composable
private fun OdometerRow(odometer: Int?, onAdjust: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            odometer?.let { stringResource(R.string.odometer_row, fmtInt(it.toDouble())) } ?: stringResource(R.string.odometer_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdjust) {
            Text(stringResource(if (odometer != null) R.string.odometer_adjust else R.string.odometer_enter))
        }
    }
}

@Composable
private fun TripsYearCard(entries: List<Entry>, year: Int, onYearChange: (Int) -> Unit, allCars: Boolean) {
    val years = entries.map { it.month.year } + YearMonth.now().year
    val stats = yearStats(entries, year)

    YearCard {
        YearHeader(stringResource(if (allCars) R.string.driven_title_all else R.string.driven_title), year, years, onYearChange)
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
                if (stats.months == 0) {
                    stringResource(R.string.months_none, year)
                } else {
                    pluralStringResource(R.plurals.months_from, stats.months, stats.months)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.stat_avg_month), stats.avgKm?.let(::fmtInt), "km", Modifier.weight(1f))
            Stat(stringResource(R.string.stat_avg_consumption), stats.avgL100?.let(::fmt1), "l/100 km", Modifier.weight(1f))
            Stat(stringResource(R.string.stat_fuel_approx), stats.liters?.let(::fmtInt), stringResource(R.string.unit_liters), Modifier.weight(1f))
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
            ColorDot(carColor(car))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(monthLong(entry.month), style = MaterialTheme.typography.bodyLarge)
            val consumption = entry.l100?.let { stringResource(R.string.consumption_value, fmt1(it)) }
                ?: stringResource(R.string.no_consumption)
            Text(
                if (car != null) stringResource(R.string.car_and_detail, car.name, consumption) else consumption,
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
        kmText.isBlank() -> R.string.err_km_empty
        km == null || km !in KM_RANGE -> R.string.err_km_range
        else -> null
    }
    val l100Error = if (l100Text.isNotBlank() && (l100 == null || l100 !in L100_RANGE)) R.string.err_l100_range else null
    val isOriginalSlot = original != null && original.carId == carId && original.month == month
    val overwrites = !isOriginalSlot && existing.any { it.carId == carId && it.month == month }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.add_month else R.string.entry_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPicker(cars, carId) { carId = it }
                MonthStepper(month, { month = it }, max = YearMonth.now())
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it },
                    label = { Text(stringResource(R.string.driven)) },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = showErrors && kmError != null,
                    supportingText = if (showErrors && kmError != null) {
                        { Text(stringResource(kmError)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = l100Text,
                    onValueChange = { l100Text = it },
                    label = { Text(stringResource(R.string.consumption_optional)) },
                    suffix = { Text("l/100 km") },
                    singleLine = true,
                    isError = showErrors && l100Error != null,
                    supportingText = if (showErrors && l100Error != null) {
                        { Text(stringResource(l100Error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overwrites) {
                    Text(
                        stringResource(R.string.entry_overwrites, monthLong(month)),
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
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (original != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/**
 * Echten Tachostand eintragen. Die Differenz zur Schätzung wird dem gewählten Monat angerechnet;
 * vorbelegt ist der zuletzt eingetragene Monat dieses Autos.
 */
@Composable
fun OdometerDialog(
    car: Car,
    entries: List<Entry>,
    onDismiss: () -> Unit,
    onSave: (actual: Int, month: YearMonth) -> Unit,
) {
    val estimate = estimateOdometer(car, entries)
    val lastMonth = entries.filter { it.carId == car.id }.maxOfOrNull { it.month }
    var month by remember { mutableStateOf(lastMonth ?: YearMonth.now().minusMonths(1)) }
    var text by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }

    val actual = parseKm(text)
    val valid = actual != null && actual in ODOMETER_RANGE
    val diff = if (valid && estimate != null) actual!! - estimate else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.odo_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (estimate != null) {
                    Text(
                        stringResource(R.string.odo_estimate, fmtInt(estimate.toDouble())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.odo_current)) },
                    suffix = { Text("km") },
                    singleLine = true,
                    isError = (showErrors || text.isNotBlank()) && !valid,
                    supportingText = if ((showErrors || text.isNotBlank()) && !valid) {
                        { Text(stringResource(R.string.err_whole_number)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (estimate != null) {
                    Text(stringResource(R.string.odo_month_label), style = MaterialTheme.typography.labelLarge)
                    MonthStepper(month, { month = it }, max = YearMonth.now())
                }
                val note = when {
                    estimate == null -> stringResource(R.string.odo_first)
                    diff == null -> null
                    diff > 0 -> stringResource(R.string.odo_plus, fmtInt(diff.toDouble()), monthLong(month))
                    diff < 0 -> stringResource(R.string.odo_minus, fmtInt(abs(diff).toDouble()), monthLong(month))
                    else -> stringResource(R.string.odo_equal)
                }
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onSave(actual!!, month) else showErrors = true }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// --- Vorschau in Android Studio (Split/Design-Ansicht), nur mit Beispieldaten ---

internal val previewCars = listOf(
    Car("golf", "Golf", 0, odometerKm = 46_500, odometerMonth = YearMonth.of(2026, 6)),
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
            TripsContent(previewCars, previewEntries, previewCars[0], 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {}, {})
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
            TripsContent(previewCars, previewEntries, null, 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {}, {})
        }
    }
}

@Preview(name = "Kilometerstand abgleichen")
@Composable
private fun OdometerDialogPreview() {
    KilometerTheme {
        OdometerDialog(car = previewCars[0], entries = previewEntries, onDismiss = {}, onSave = { _, _ -> })
    }
}
