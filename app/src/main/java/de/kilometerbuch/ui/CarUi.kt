package de.kilometerbuch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.CAR_COLOR_COUNT
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.ODOMETER_RANGE
import de.kilometerbuch.data.parseKm
import de.kilometerbuch.ui.theme.carColor
import de.kilometerbuch.ui.theme.paletteColor
import android.graphics.Color as AndroidColor

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
                ColorDot(carColor(selectedCar))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                selectedCar?.name ?: stringResource(R.string.all_cars),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.choose_car))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_cars)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_car), contentDescription = null) },
                trailingIcon = { if (selectedCar == null) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected)) },
                onClick = {
                    onSelect(ALL_CARS)
                    open = false
                },
            )
            HorizontalDivider()
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    leadingIcon = { ColorDot(carColor(car)) },
                    trailingIcon = { if (selectedCar?.id == car.id) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.selected)) },
                    onClick = {
                        onSelect(car.id)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_car)) },
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
            ColorDot(carColor(selected))
            Spacer(Modifier.width(10.dp))
            Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.choose_car))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cars.forEach { car ->
                DropdownMenuItem(
                    text = { Text(car.name) },
                    leadingIcon = { ColorDot(carColor(car)) },
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

/** Ergebnis des Auto-Formulars; Kilometerstand und Baujahr sind optional, [customColor] ersetzt die Palettenfarbe. */
data class CarInput(
    val name: String,
    val odometerKm: Int?,
    val buildYear: Int?,
    val colorIndex: Int,
    val customColor: Int?,
)

private const val MIN_BUILD_YEAR = 1950

/** Eingaben und Prüfung für ein Auto. Fehler sind Text-Ids, damit die Prüfung keine Sprache kennen muss. */
class CarFormState(name: String, odometer: Int?, buildYear: Int?, colorIndex: Int, customColor: Int?) {
    var name by mutableStateOf(name)
    var odometerText by mutableStateOf(odometer?.toString() ?: "")
    var buildYearText by mutableStateOf(buildYear?.toString() ?: "")
    var colorIndex by mutableStateOf(colorIndex)
    var customColor by mutableStateOf(customColor)

    val thisYear = java.time.Year.now().value
    private val odometer get() = if (odometerText.isBlank()) null else parseKm(odometerText)
    private val buildYear get() = buildYearText.trim().toIntOrNull()

    fun nameError(cars: List<Car>, ownId: String?): Int? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> R.string.err_name_empty
            trimmed.length > 30 -> R.string.err_name_long
            cars.any { it.id != ownId && it.name.equals(trimmed, ignoreCase = true) } -> R.string.err_name_taken
            else -> null
        }
    }

    val odometerError: Int?
        get() = if (odometerText.isNotBlank() && odometer?.let { it in ODOMETER_RANGE } != true) R.string.err_whole_number else null

    val buildYearError: Boolean
        get() = buildYearText.isNotBlank() && buildYear?.let { it in MIN_BUILD_YEAR..thisYear } != true

    /** Das Ergebnis, oder null, solange etwas nicht stimmt. */
    fun toInput(cars: List<Car>, ownId: String?): CarInput? =
        if (nameError(cars, ownId) == null && odometerError == null && !buildYearError) {
            CarInput(name.trim(), odometer, buildYear, colorIndex, customColor)
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
            label = { Text(stringResource(R.string.car_name)) },
            placeholder = { Text(stringResource(R.string.car_name_placeholder)) },
            singleLine = true,
            isError = showErrors && nameError != null,
            supportingText = if (showErrors && nameError != null) {
                { Text(stringResource(nameError)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        CarColorPicker(
            colorIndex = state.colorIndex,
            customColor = state.customColor,
            onPalette = {
                state.colorIndex = it
                state.customColor = null
            },
            onCustom = { state.customColor = it },
        )
        OutlinedTextField(
            value = state.odometerText,
            onValueChange = { state.odometerText = it },
            label = { Text(stringResource(R.string.odometer_optional)) },
            suffix = { Text("km") },
            singleLine = true,
            isError = state.odometerError != null,
            supportingText = {
                Text(
                    stringResource(
                        state.odometerError ?: if (hasOdometer) R.string.odometer_hint_existing else R.string.odometer_hint_new,
                    ),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.buildYearText,
            onValueChange = { state.buildYearText = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.build_year_optional)) },
            singleLine = true,
            isError = state.buildYearError,
            supportingText = if (state.buildYearError) {
                { Text(stringResource(R.string.err_year_range, MIN_BUILD_YEAR, state.thisYear)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Ein runder Farbknopf; ausgewählt mit Ring und Haken. */
@Composable
private fun ColorSwatch(color: Color?, selected: Boolean, contentDescription: String, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .padding(if (selected) 5.dp else 2.dp)
            .clip(CircleShape)
            .background(
                if (color != null) Brush.linearGradient(listOf(color, color)) else HUE_BRUSH,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = contentDescription, tint = Color.White)
    }
}

private val HUE_BRUSH = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
)

private const val CUSTOM_SATURATION = 0.75f

/** Farbwahl: die 8 abgestimmten Palettenfarben oder eine eigene Farbe über Farbton und Helligkeit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarColorPicker(colorIndex: Int, customColor: Int?, onPalette: (Int) -> Unit, onCustom: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.car_color), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val selectedLabel = stringResource(R.string.selected)
            (0 until CAR_COLOR_COUNT).forEach { i ->
                ColorSwatch(paletteColor(i), selected = customColor == null && colorIndex == i, selectedLabel) { onPalette(i) }
            }
            ColorSwatch(customColor?.let { Color(it) }, selected = customColor != null, stringResource(R.string.color_custom)) {
                if (customColor == null) onCustom(AndroidColor.HSVToColor(floatArrayOf(200f, CUSTOM_SATURATION, 0.8f)))
            }
        }
        if (customColor != null) {
            val hsv = FloatArray(3).also { AndroidColor.colorToHSV(customColor, it) }
            val current = Color(customColor)
            Text(stringResource(R.string.color_hue), style = MaterialTheme.typography.bodySmall)
            GradientSlider(
                value = hsv[0],
                range = 0f..360f,
                brush = Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                ),
                thumb = current,
            ) { onCustom(AndroidColor.HSVToColor(floatArrayOf(it, CUSTOM_SATURATION, hsv[2]))) }
            Text(stringResource(R.string.color_brightness), style = MaterialTheme.typography.bodySmall)
            val full = Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], CUSTOM_SATURATION, 1f)))
            GradientSlider(
                value = hsv[2],
                range = 0.35f..1f,
                brush = Brush.horizontalGradient(listOf(Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0], CUSTOM_SATURATION, 0.35f))), full)),
                thumb = current,
            ) { onCustom(AndroidColor.HSVToColor(floatArrayOf(hsv[0], CUSTOM_SATURATION, it))) }
        }
    }
}

/** Schieberegler über einem Farbverlauf. */
@Composable
private fun GradientSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    thumb: Color,
    onChange: (Float) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush),
        )
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = thumb,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
            ),
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
    newColorIndex: Int,
    onDismiss: () -> Unit,
    onSave: (CarInput) -> Unit,
    onDelete: () -> Unit,
) {
    val form = remember {
        CarFormState(
            original?.name ?: "",
            currentOdometer,
            original?.buildYear,
            original?.colorIndex ?: newColorIndex,
            original?.customColor,
        )
    }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (original == null) R.string.add_car else R.string.edit_car)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                CarFields(form, cars, original?.id, showErrors, hasOdometer = original?.odometerKm != null)
            }
        },
        confirmButton = {
            TextButton(onClick = { form.toInput(cars, original?.id)?.let(onSave) ?: run { showErrors = true } }) {
                Text(stringResource(R.string.save))
            }
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
