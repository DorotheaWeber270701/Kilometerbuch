package de.kilometerbuch.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import de.kilometerbuch.data.AMOUNT_RANGE
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.FuelReceipt
import de.kilometerbuch.data.LITERS_RANGE
import de.kilometerbuch.data.MaintenanceCategory
import de.kilometerbuch.data.MaintenanceCost
import de.kilometerbuch.data.TOTAL_RANGE
import de.kilometerbuch.data.parseDecimal
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.allCarsColor
import de.kilometerbuch.ui.theme.carColor
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Seite „Tanken“ mit Wartungskosten. [selectedCar] ist null in der Gesamtansicht über alle Autos. */
@Composable
fun FuelContent(
    cars: List<Car>,
    receipts: List<FuelReceipt>,
    maintenance: List<MaintenanceCost>,
    selectedCar: Car?,
    year: Int,
    onYearChange: (Int) -> Unit,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    contentPadding: PaddingValues,
    onEdit: (FuelReceipt) -> Unit,
    onAddMaintenance: () -> Unit,
    onEditMaintenance: (MaintenanceCost) -> Unit,
) {
    val shownCars = selectedCar?.let(::listOf) ?: cars
    val shownIds = shownCars.map { it.id }.toSet()
    val shown = receipts.filter { it.carId in shownIds }
    val shownMaintenance = maintenance.filter { it.carId in shownIds }
    val carsById = cars.associateBy { it.id }
    val overview = selectedCar == null && cars.size > 1

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { FuelYearCard(shown, year, onYearChange, allCars = selectedCar == null) }

        if (shown.isEmpty()) {
            item {
                EmptyCard(
                    if (selectedCar != null) {
                        stringResource(R.string.fuel_empty_title_car, selectedCar.name)
                    } else {
                        stringResource(R.string.fuel_empty_title)
                    },
                    stringResource(R.string.fuel_empty_text),
                )
            }
        } else {
            val perCar = shownCars.associate { car -> car.id to fuelByMonth(shown.filter { it.carId == car.id }) }
            val combined = fuelByMonth(shown)
            val months = monthRange(combined.keys, range)

            if (overview) {
                item {
                    BreakdownCard(
                        stringResource(R.string.per_car_in_year, year),
                        cars.map { car ->
                            val st = fuelYearStats(shown.filter { it.carId == car.id }, year)
                            BreakdownRow(
                                color = carColor(car),
                                name = car.name,
                                value = "${fmt2(st.euros)} €",
                                detail = if (st.receipts == 0) {
                                    stringResource(R.string.no_receipt)
                                } else {
                                    stringResource(R.string.liters_avg_price, fmtInt(st.liters), st.avgPrice?.let(::fmt3) ?: "–")
                                },
                            )
                        },
                    )
                }
            }
            item { RangeSelector(range, onRangeChange) }
            item {
                ChartCard(
                    stringResource(R.string.chart_spend),
                    stringResource(if (overview) R.string.chart_spend_sub_stacked else R.string.chart_spend_sub),
                ) { surface ->
                    MonthChart(
                        months = months,
                        series = shownCars.map { car ->
                            ChartSeries(car.name, carColor(car), months.map { perCar[car.id]?.get(it)?.euros?.toFloat() })
                        }.filter { s -> s.values.any { it != null } },
                        kind = ChartKind.Bars,
                        unit = "€",
                        format = { fmt2(it.toDouble()) },
                        axisFormat = { fmtInt(it.toDouble()) },
                        surfaceColor = surface,
                        showAverage = true,
                    )
                }
            }
            item {
                ChartCard(
                    stringResource(R.string.chart_price),
                    stringResource(if (selectedCar == null) R.string.chart_price_sub_all else R.string.chart_price_sub),
                ) { surface ->
                    MonthChart(
                        months = months,
                        series = listOf(
                            ChartSeries(
                                name = selectedCar?.name ?: stringResource(R.string.all_cars),
                                color = selectedCar?.let { carColor(it) } ?: allCarsColor(),
                                values = months.map { combined[it]?.pricePerLiter?.toFloat() },
                            ),
                        ),
                        kind = ChartKind.Line,
                        unit = "€/l",
                        format = { fmt3(it.toDouble()) },
                        axisFormat = { fmt2(it.toDouble()) },
                        surfaceColor = surface,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.receipts_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(shown.asReversed(), key = { it.id }) { receipt ->
                ReceiptRow(receipt, if (overview) carsById[receipt.carId] else null) { onEdit(receipt) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Wartung: bewusst klein am Ende der Seite.
        item {
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.maint_header), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (shownMaintenance.isEmpty()) {
                        stringResource(R.string.maint_empty)
                    } else {
                        stringResource(R.string.maint_sum, fmt2(maintenanceSum(shownMaintenance, year)), year)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(shownMaintenance.asReversed(), key = { "wartung-${it.id}" }) { cost ->
            MaintenanceRow(cost, if (overview) carsById[cost.carId] else null) { onEditMaintenance(cost) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            OutlinedButton(onClick = onAddMaintenance, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.maint_add))
            }
        }
    }
}

@Composable
private fun FuelYearCard(receipts: List<FuelReceipt>, year: Int, onYearChange: (Int) -> Unit, allCars: Boolean) {
    val years = receipts.map { it.date.year } + YearMonth.now().year
    val stats = fuelYearStats(receipts, year)

    YearCard {
        YearHeader(stringResource(if (allCars) R.string.spend_title_all else R.string.spend_title), year, years, onYearChange)
        Column {
            Row {
                Text(
                    fmt2(stats.euros),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(Modifier.width(8.dp))
                Text("€", style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
            }
            Text(
                if (stats.receipts == 0) {
                    stringResource(R.string.receipts_none, year)
                } else {
                    pluralStringResource(R.plurals.receipts_from, stats.receipts, stats.receipts)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Stat(stringResource(R.string.stat_avg_month), stats.avgPerMonth?.let(::fmt2), "€", Modifier.weight(1f))
            Stat(stringResource(R.string.stat_avg_price), stats.avgPrice?.let(::fmt3), "€/l", Modifier.weight(1f))
            Stat(
                stringResource(R.string.stat_refueled),
                if (stats.receipts == 0) null else fmtInt(stats.liters),
                stringResource(R.string.unit_liters),
                Modifier.weight(1f),
            )
        }
    }
}

/** [car] wird nur in der Gesamtansicht übergeben und dann mit angezeigt. */
@Composable
private fun ReceiptRow(receipt: FuelReceipt, car: Car?, onClick: () -> Unit) {
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
            Text(fmtDate(receipt.date), style = MaterialTheme.typography.bodyLarge)
            val details = stringResource(R.string.receipt_details, fmt2(receipt.liters), fmt3(receipt.pricePerLiter))
            Text(
                if (car != null) stringResource(R.string.car_and_detail, car.name, details) else details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${fmt2(receipt.total)} €",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@StringRes
fun MaintenanceCategory.label(): Int = when (this) {
    MaintenanceCategory.INSPECTION -> R.string.maint_cat_inspection
    MaintenanceCategory.REPAIR -> R.string.maint_cat_repair
    MaintenanceCategory.TIRES -> R.string.maint_cat_tires
    MaintenanceCategory.HU -> R.string.maint_cat_hu
    MaintenanceCategory.OTHER -> R.string.maint_cat_other
}

@Composable
private fun MaintenanceRow(cost: MaintenanceCost, car: Car?, onClick: () -> Unit) {
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
            Text(stringResource(cost.category.label()), style = MaterialTheme.typography.bodyLarge)
            val details = listOfNotNull(car?.name, fmtDate(cost.date), cost.note).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${fmt2(cost.amount)} €", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ReceiptDialog(
    original: FuelReceipt?,
    cars: List<Car>,
    initialCarId: String,
    onDismiss: () -> Unit,
    onSave: (FuelReceipt) -> Unit,
    onDelete: () -> Unit,
) {
    var carId by remember { mutableStateOf(original?.carId ?: initialCarId) }
    var date by remember { mutableStateOf<LocalDate?>(original?.date ?: LocalDate.now()) }
    var litersText by remember { mutableStateOf(original?.liters?.let(::decimalInput) ?: "") }
    var totalText by remember { mutableStateOf(original?.total?.let(::decimalInput) ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val liters = parseDecimal(litersText)
    val total = parseDecimal(totalText)
    val litersError = when {
        litersText.isBlank() -> R.string.err_liters_empty
        liters == null || liters !in LITERS_RANGE -> R.string.err_liters_range
        else -> null
    }
    val totalError = when {
        totalText.isBlank() -> R.string.err_total_empty
        total == null || total !in TOTAL_RANGE -> R.string.err_total_range
        else -> null
    }
    val today = LocalDate.now()
    val price = if (litersError == null && totalError == null && liters != null && total != null) total / liters else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.add_receipt else R.string.receipt_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPicker(cars, carId) { carId = it }
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.date),
                    showErrors = showErrors,
                    validate = { if (it.isAfter(today)) stringResource(R.string.err_date_future) else null },
                    modifier = Modifier.fillMaxWidth(),
                )
                DecimalField(litersText, { litersText = it }, R.string.refueled_amount, "l", if (showErrors) litersError else null)
                DecimalField(totalText, { totalText = it }, R.string.total_cost, "€", if (showErrors) totalError else null, last = true)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.price_per_liter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        price?.let { "${fmt3(it)} €/l" } ?: stringResource(R.string.price_calculated),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (price != null) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = date
                if (day != null && !day.isAfter(today) && liters != null && total != null && litersError == null && totalError == null) {
                    onSave(FuelReceipt(original?.id ?: UUID.randomUUID().toString(), carId, day, liters, total))
                } else {
                    showErrors = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { DeleteAndCancel(original != null, onDelete, onDismiss) },
    )
}

/** Wartungskosten erfassen oder bearbeiten. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaintenanceDialog(
    original: MaintenanceCost?,
    cars: List<Car>,
    initialCarId: String,
    onDismiss: () -> Unit,
    onSave: (MaintenanceCost) -> Unit,
    onDelete: () -> Unit,
) {
    var carId by remember { mutableStateOf(original?.carId ?: initialCarId) }
    var date by remember { mutableStateOf<LocalDate?>(original?.date ?: LocalDate.now()) }
    var category by remember { mutableStateOf(original?.category ?: MaintenanceCategory.INSPECTION) }
    var amountText by remember { mutableStateOf(original?.amount?.let(::decimalInput) ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val amount = parseDecimal(amountText)
    val amountError = when {
        amountText.isBlank() -> R.string.err_amount_empty
        amount == null || amount !in AMOUNT_RANGE -> R.string.err_amount_range
        else -> null
    }
    val today = LocalDate.now()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.maint_add else R.string.maint_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPicker(cars, carId) { carId = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MaintenanceCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(stringResource(c.label())) })
                    }
                }
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = stringResource(R.string.date),
                    showErrors = showErrors,
                    validate = { if (it.isAfter(today)) stringResource(R.string.err_date_future) else null },
                    modifier = Modifier.fillMaxWidth(),
                )
                DecimalField(amountText, { amountText = it }, R.string.maint_amount, "€", if (showErrors) amountError else null)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text(stringResource(R.string.maint_note)) },
                    placeholder = { Text(stringResource(R.string.maint_note_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val day = date
                if (day != null && !day.isAfter(today) && amount != null && amountError == null) {
                    onSave(
                        MaintenanceCost(
                            original?.id ?: UUID.randomUUID().toString(),
                            carId, day, category, amount, note.trim().ifBlank { null },
                        ),
                    )
                } else {
                    showErrors = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { DeleteAndCancel(original != null, onDelete, onDismiss) },
    )
}

/** Eingabefeld für Kommazahlen mit Einheit; [error] ist eine Text-Id oder null. */
@Composable
private fun DecimalField(
    value: String,
    onChange: (String) -> Unit,
    @StringRes label: Int,
    unit: String,
    @StringRes error: Int?,
    last: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        suffix = { Text(unit) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeleteAndCancel(canDelete: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    Row {
        if (canDelete) {
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    }
}

// --- Vorschau in Android Studio (Split/Design-Ansicht), nur mit Beispieldaten ---

private val previewReceipts = listOf(
    Triple(LocalDate.of(2025, 11, 4), 41.2, 72.48),
    Triple(LocalDate.of(2025, 11, 25), 38.7, 69.27),
    Triple(LocalDate.of(2025, 12, 18), 44.0, 77.40),
    Triple(LocalDate.of(2026, 1, 12), 40.5, 70.83),
    Triple(LocalDate.of(2026, 2, 9), 39.8, 69.21),
    Triple(LocalDate.of(2026, 3, 6), 42.6, 76.21),
    Triple(LocalDate.of(2026, 3, 28), 37.9, 68.94),
    Triple(LocalDate.of(2026, 4, 22), 43.1, 79.26),
    Triple(LocalDate.of(2026, 5, 19), 45.3, 81.95),
    Triple(LocalDate.of(2026, 6, 14), 41.0, 72.53),
    Triple(LocalDate.of(2026, 7, 3), 46.2, 79.42),
    Triple(LocalDate.of(2026, 7, 27), 44.8, 77.89),
    Triple(LocalDate.of(2026, 8, 21), 42.4, 74.15),
    Triple(LocalDate.of(2026, 9, 16), 40.9, 72.36),
).mapIndexed { i, (d, l, eur) -> FuelReceipt("p$i", if (i % 3 == 0) "firma" else "golf", d, l, eur) }

private val previewMaintenance = listOf(
    MaintenanceCost("m1", "golf", LocalDate.of(2026, 3, 12), MaintenanceCategory.INSPECTION, 289.90, "Ölwechsel"),
    MaintenanceCost("m2", "golf", LocalDate.of(2026, 10, 1), MaintenanceCategory.TIRES, 64.00),
)

@Preview(name = "Tanken, ein Auto", showBackground = true, heightDp = 2000)
@Composable
private fun FuelPreview() {
    KilometerTheme {
        Surface {
            FuelContent(
                previewCars, previewReceipts, previewMaintenance, previewCars[0], 2026, {}, ChartRange.M12, {},
                PaddingValues(16.dp), {}, {}, {},
            )
        }
    }
}

@Preview(
    name = "Tanken, alle Autos (dunkel)",
    showBackground = true,
    heightDp = 2200,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FuelAllPreview() {
    KilometerTheme {
        Surface {
            FuelContent(
                previewCars, previewReceipts, previewMaintenance, null, 2026, {}, ChartRange.M12, {},
                PaddingValues(16.dp), {}, {}, {},
            )
        }
    }
}

@Preview(name = "Wartungskosten eintragen")
@Composable
private fun MaintenanceDialogPreview() {
    KilometerTheme {
        MaintenanceDialog(original = null, cars = previewCars, initialCarId = "golf", onDismiss = {}, onSave = {}, onDelete = {})
    }
}
