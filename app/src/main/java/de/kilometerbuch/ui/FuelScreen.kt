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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.FuelReceipt
import de.kilometerbuch.data.LITERS_RANGE
import de.kilometerbuch.data.TOTAL_RANGE
import de.kilometerbuch.data.parseDecimal
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.allCarsColor
import de.kilometerbuch.ui.theme.carColor
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Seite „Tanken“. [selectedCar] ist null in der Gesamtansicht über alle Autos. */
@Composable
fun FuelContent(
    cars: List<Car>,
    receipts: List<FuelReceipt>,
    selectedCar: Car?,
    year: Int,
    onYearChange: (Int) -> Unit,
    range: ChartRange,
    onRangeChange: (ChartRange) -> Unit,
    contentPadding: PaddingValues,
    onEdit: (FuelReceipt) -> Unit,
) {
    val shownCars = selectedCar?.let(::listOf) ?: cars
    val shownIds = shownCars.map { it.id }.toSet()
    val shown = receipts.filter { it.carId in shownIds }
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
                    if (selectedCar != null) "Für „${selectedCar.name}“ gibt es noch keine Tankbelege" else "Noch keine Tankbelege",
                    "Tippe unten auf „Tankbeleg eintragen“ und gib die getankten Liter und den Gesamtbetrag ein. " +
                        "Den Preis pro Liter rechnet die App selbst aus.",
                )
            }
        } else {
            val perCar = shownCars.associate { car -> car.id to fuelByMonth(shown.filter { it.carId == car.id }) }
            val combined = fuelByMonth(shown)
            val months = monthRange(combined.keys, range)

            if (overview) {
                item {
                    BreakdownCard(
                        "Pro Auto in $year",
                        cars.map { car ->
                            val st = fuelYearStats(shown.filter { it.carId == car.id }, year)
                            BreakdownRow(
                                color = carColor(car.colorIndex),
                                name = car.name,
                                value = "${fmt2(st.euros)} €",
                                detail = if (st.receipts == 0) {
                                    "Kein Tankbeleg"
                                } else {
                                    "${fmtInt(st.liters)} l · Ø ${st.avgPrice?.let(::fmt3) ?: "–"} €/l"
                                },
                            )
                        },
                    )
                }
            }
            item { RangeSelector(range, onRangeChange) }
            item {
                ChartCard(
                    "Ausgaben pro Monat",
                    if (overview) "Gestapelt nach Auto · gestrichelt: Durchschnitt pro Monat" else "Gestrichelt: Durchschnitt pro Monat",
                ) { surface ->
                    MonthChart(
                        months = months,
                        series = shownCars.map { car ->
                            ChartSeries(car.name, carColor(car.colorIndex), months.map { perCar[car.id]?.get(it)?.euros?.toFloat() })
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
                    "Preis pro Liter",
                    if (selectedCar == null) "Alle Autos zusammen, Durchschnitt aller Belege im Monat" else "Durchschnitt aller Belege im Monat",
                ) { surface ->
                    MonthChart(
                        months = months,
                        series = listOf(
                            ChartSeries(
                                name = selectedCar?.name ?: "Alle Autos",
                                color = selectedCar?.let { carColor(it.colorIndex) } ?: allCarsColor(),
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
                Text("Tankbelege", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(shown.asReversed(), key = { it.id }) { receipt ->
                ReceiptRow(receipt, if (overview) carsById[receipt.carId] else null) { onEdit(receipt) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun FuelYearCard(receipts: List<FuelReceipt>, year: Int, onYearChange: (Int) -> Unit, allCars: Boolean) {
    val years = receipts.map { it.date.year } + YearMonth.now().year
    val stats = fuelYearStats(receipts, year)

    YearCard {
        YearHeader(if (allCars) "Ausgaben, alle Autos" else "Ausgaben", year, years, onYearChange)
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
                when (stats.receipts) {
                    0 -> "In $year noch nicht getankt"
                    1 -> "aus 1 Tankbeleg"
                    else -> "aus ${stats.receipts} Tankbelegen"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Stat("Ø pro Monat", stats.avgPerMonth?.let(::fmt2), "€", Modifier.weight(1f))
            Stat("Ø Preis", stats.avgPrice?.let(::fmt3), "€/l", Modifier.weight(1f))
            Stat("Getankt", if (stats.receipts == 0) null else fmtInt(stats.liters), "Liter", Modifier.weight(1f))
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
            ColorDot(carColor(car.colorIndex))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(fmtDate(receipt.date), style = MaterialTheme.typography.bodyLarge)
            val details = "${fmt2(receipt.liters)} l · ${fmt3(receipt.pricePerLiter)} €/l"
            Text(
                if (car != null) "${car.name} · $details" else details,
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
        litersText.isBlank() -> "Bitte die getankten Liter eintragen."
        liters == null || liters !in LITERS_RANGE -> "Bitte einen Wert zwischen 0,5 und 300 eingeben, z. B. 42,5."
        else -> null
    }
    val totalError = when {
        totalText.isBlank() -> "Bitte den Gesamtbetrag eintragen."
        total == null || total !in TOTAL_RANGE -> "Bitte einen Betrag zwischen 0,50 und 2.000 € eingeben, z. B. 76,03."
        else -> null
    }
    val today = LocalDate.now()
    val price = if (litersError == null && totalError == null && liters != null && total != null) total / liters else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Tankbeleg eintragen" else "Tankbeleg bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CarPicker(cars, carId) { carId = it }
                DateField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Datum",
                    showErrors = showErrors,
                    validate = { if (it.isAfter(today)) "Das Datum liegt in der Zukunft." else null },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("Getankt") },
                    suffix = { Text("l") },
                    singleLine = true,
                    isError = showErrors && litersError != null,
                    supportingText = if (showErrors && litersError != null) {
                        { Text(litersError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { totalText = it },
                    label = { Text("Gesamtkosten") },
                    suffix = { Text("€") },
                    singleLine = true,
                    isError = showErrors && totalError != null,
                    supportingText = if (showErrors && totalError != null) {
                        { Text(totalError) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Preis pro Liter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        price?.let { "${fmt3(it)} €/l" } ?: "wird berechnet",
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

@Preview(name = "Tanken, ein Auto", showBackground = true, heightDp = 1600)
@Composable
private fun FuelPreview() {
    KilometerTheme {
        Surface {
            FuelContent(previewCars, previewReceipts, previewCars[0], 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {})
        }
    }
}

@Preview(
    name = "Tanken, alle Autos (dunkel)",
    showBackground = true,
    heightDp = 1900,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FuelAllPreview() {
    KilometerTheme {
        Surface {
            FuelContent(previewCars, previewReceipts, null, 2026, {}, ChartRange.M12, {}, PaddingValues(16.dp), {})
        }
    }
}

@Preview(name = "Tankbeleg eintragen")
@Composable
private fun ReceiptDialogPreview() {
    KilometerTheme {
        ReceiptDialog(
            original = previewReceipts.last(), cars = previewCars, initialCarId = "golf",
            onDismiss = {}, onSave = {}, onDelete = {},
        )
    }
}
