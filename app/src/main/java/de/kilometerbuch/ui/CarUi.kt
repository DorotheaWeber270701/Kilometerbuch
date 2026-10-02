package de.kilometerbuch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.ODOMETER_RANGE
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.ui.theme.carColor

/** Auswahl „Alle Autos“ für die Gesamtansicht. */
const val ALL_CARS = "__alle__"

@Composable
fun ColorDot(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** Autowahl oben links in der Titelleiste. */
@Composable
fun CarSelector(
    cars: List<Car>,
    selectedCar: Car?,
    onSelect: (String) -> Unit,
    onAddCar: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clickable { open = true }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectedCar != null) {
                ColorDot(carColor(selectedCar.colorIndex))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                selectedCar?.name ?: "Alle Autos",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Auto wählen")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Alle Autos") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_car), contentDescription = null) },
                trailingIcon = { if (selectedCar == null) Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") },
                onClick = {
                    onSelect(ALL_CARS)
                    open = false
                },
            )
            HorizontalDivider()
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    leadingIcon = { ColorDot(carColor(car.colorIndex)) },
                    trailingIcon = { if (selectedCar?.id == car.id) Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") },
                    onClick = {
                        onSelect(car.id)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Auto hinzufügen") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    open = false
                    onAddCar()
                },
            )
        }
    }
}

/** Autowahl in den Eintragen-Dialogen; erscheint nur bei mehr als einem Auto. */
@Composable
fun CarPicker(cars: List<Car>, selectedId: String, onSelect: (String) -> Unit) {
    if (cars.size < 2) return
    val selected = cars.find { it.id == selectedId } ?: cars.first()
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            ColorDot(carColor(selected.colorIndex))
            Spacer(Modifier.width(10.dp))
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Auto wählen")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    leadingIcon = { ColorDot(carColor(car.colorIndex)) },
                    onClick = {
                        onSelect(car.id)
                        open = false
                    },
                )
            }
        }
    }
}

data class BreakdownRow(val color: Color, val name: String, val value: String, val detail: String)

/** Kurzübersicht je Auto in der Gesamtansicht. */
@Composable
fun BreakdownCard(title: String, rows: List<BreakdownRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(row.color)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            row.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(row.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Ergebnis des Auto-Formulars; Kilometerstand und Baujahr sind optional. */
data class CarInput(val name: String, val odometerKm: Int?, val buildYear: Int?)

/** Eingaben und Prüfung für Name, Kilometerstand und Baujahr eines Autos. */
class CarFormState(name: String, odometer: Int?, buildYear: Int?) {
    var name by mutableStateOf(name)
    var odometerText by mutableStateOf(odometer?.toString() ?: "")
    var buildYearText by mutableStateOf(buildYear?.toString() ?: "")

    private val thisYear = java.time.Year.now().value
    private val odometer get() = if (odometerText.isBlank()) null else parseKm(odometerText)
    private val buildYear get() = buildYearText.trim().toIntOrNull()

    fun nameError(cars: List<Car>, ownId: String?): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Bitte einen Namen eingeben, z. B. „Golf“ oder „Firmenwagen“."
            trimmed.length > 30 -> "Bitte höchstens 30 Zeichen verwenden."
            cars.any { it.id != ownId && it.name.equals(trimmed, ignoreCase = true) } -> "Es gibt schon ein Auto mit diesem Namen."
            else -> null
        }
    }

    val odometerError: String?
        get() = if (odometerText.isNotBlank() && odometer?.let { it in ODOMETER_RANGE } != true) {
            "Bitte eine ganze Zahl eingeben, z. B. 48.300."
        } else {
            null
        }

    val buildYearError: String?
        get() = if (buildYearText.isNotBlank() && buildYear?.let { it in 1950..thisYear } != true) {
            "Bitte ein Jahr zwischen 1950 und $thisYear eingeben."
        } else {
            null
        }

    /** Das Ergebnis, oder null, solange etwas nicht stimmt. */
    fun toInput(cars: List<Car>, ownId: String?): CarInput? =
        if (nameError(cars, ownId) == null && odometerError == null && buildYearError == null) {
            CarInput(name.trim(), odometer, buildYear)
        } else {
            null
        }
}

/** Die Felder für ein Auto; [hasOdometer] steuert nur den Hinweistext unter dem Kilometerstand. */
@Composable
fun CarFields(
    state: CarFormState,
    cars: List<Car>,
    ownId: String?,
    showErrors: Boolean,
    hasOdometer: Boolean = false,
) {
    val nameError = state.nameError(cars, ownId)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = { state.name = it },
            label = { Text("Name") },
            placeholder = { Text("z. B. Golf") },
            singleLine = true,
            isError = showErrors && nameError != null,
            supportingText = if (showErrors && nameError != null) {
                { Text(nameError) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.odometerText,
            onValueChange = { state.odometerText = it },
            label = { Text("Kilometerstand (optional)") },
            suffix = { Text("km") },
            singleLine = true,
            isError = state.odometerError != null,
            supportingText = {
                Text(
                    state.odometerError ?: if (hasOdometer) {
                        "Wird mit deinen Monatskilometern fortgeschrieben."
                    } else {
                        "Für Inspektion und Zahnriemen. Danach zählt die App selbst weiter."
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.buildYearText,
            onValueChange = { state.buildYearText = it.filter(Char::isDigit).take(4) },
            label = { Text("Baujahr (optional)") },
            singleLine = true,
            isError = state.buildYearError != null,
            supportingText = state.buildYearError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Auto anlegen oder bearbeiten. [original] ist null für ein neues Auto.
 * [currentOdometer] ist der fortgeschriebene Kilometerstand, der beim Bearbeiten vorbelegt wird.
 */
@Composable
fun CarDialog(
    original: Car?,
    cars: List<Car>,
    currentOdometer: Int?,
    onDismiss: () -> Unit,
    onSave: (CarInput) -> Unit,
    onDelete: () -> Unit,
) {
    val form = remember { CarFormState(original?.name ?: "", currentOdometer, original?.buildYear) }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (original == null) "Auto hinzufügen" else "Auto bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CarFields(form, cars, original?.id, showErrors, hasOdometer = original?.odometerKm != null)
            }
        },
        confirmButton = {
            TextButton(onClick = { form.toInput(cars, original?.id)?.let(onSave) ?: run { showErrors = true } }) {
                Text("Speichern")
            }
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

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Hamburger-Symbol, das sich mit [progress] von 0 nach 1 in ein X verwandelt. */
@Composable
fun MenuCloseIcon(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val s = size.width / 24f
        val stroke = 2.dp.toPx()
        val startX = 4f * s
        val endX = 20f * s
        val centerX = 12f * s

        val topY = lerp(6f, 12f, progress) * s
        rotate(45f * progress, pivot = Offset(centerX, topY)) {
            drawLine(color, Offset(startX, topY), Offset(endX, topY), stroke, StrokeCap.Round)
        }
        drawLine(color.copy(alpha = color.alpha * (1f - progress)), Offset(startX, 12f * s), Offset(endX, 12f * s), stroke, StrokeCap.Round)
        val bottomY = lerp(18f, 12f, progress) * s
        rotate(-45f * progress, pivot = Offset(centerX, bottomY)) {
            drawLine(color, Offset(startX, bottomY), Offset(endX, bottomY), stroke, StrokeCap.Round)
        }
    }
}
