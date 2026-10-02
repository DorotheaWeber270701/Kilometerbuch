package de.kilometerbuch.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

/** Zeigt getippte Ziffern als TT.MM.JJJJ an; die Punkte setzt das Feld selbst. */
private object DateDotsTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('.')
                append(c)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int) = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

private fun LocalDate.toDigits() = String.format(Locale.ROOT, "%02d%02d%04d", dayOfMonth, monthValue, year)

private fun parseDigits(digits: String): LocalDate? {
    if (digits.length != 8) return null
    return runCatching {
        LocalDate.of(digits.substring(4).toInt(), digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt())
    }.getOrNull()
}

/**
 * Datumsfeld zum Tippen: nur Ziffern, Punkte kommen automatisch. Darunter steht zur Kontrolle der
 * Wochentag. [onValueChange] bekommt null, solange kein gültiges Datum eingegeben ist.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    showErrors: Boolean = false,
    /** Zusätzliche Prüfung, z. B. „nicht in der Zukunft“; gibt einen Fehlertext oder null zurück. */
    validate: (LocalDate) -> String? = { null },
) {
    var digits by remember { mutableStateOf(value?.toDigits() ?: "") }
    val parsed = parseDigits(digits)
    val problem = when {
        digits.length == 8 && parsed == null -> "Dieses Datum gibt es nicht."
        parsed != null -> validate(parsed)
        showErrors -> "Bitte das Datum als TT.MM.JJJJ eingeben, z. B. 14102026."
        else -> null
    }

    fun set(newDigits: String) {
        digits = newDigits
        onValueChange(parseDigits(newDigits))
    }

    OutlinedTextField(
        value = digits,
        onValueChange = { input -> set(input.filter(Char::isDigit).take(8)) },
        label = { Text(label) },
        placeholder = { Text("TT.MM.JJJJ") },
        singleLine = true,
        visualTransformation = DateDotsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        trailingIcon = { TextButton(onClick = { set(LocalDate.now().toDigits()) }) { Text("Heute") } },
        isError = problem != null,
        supportingText = {
            when {
                problem != null -> Text(problem)
                parsed != null -> Text(parsed.format(LONG_DATE))
                else -> Text("Nur Ziffern tippen, die Punkte kommen von selbst.")
            }
        },
        modifier = modifier,
    )
}
