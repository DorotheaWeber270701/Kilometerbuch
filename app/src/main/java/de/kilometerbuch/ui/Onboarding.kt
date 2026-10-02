package de.kilometerbuch.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.ui.theme.KilometerTheme

private data class GuideStep(@DrawableRes val icon: Int, val title: String, val text: String)

private val GUIDE_STEPS = listOf(
    GuideStep(
        R.drawable.ic_car,
        "Willkommen beim Kilometerbuch",
        "Behalte im Blick, wie viel du fährst, was das Tanken kostet und wann TÜV und Inspektion fällig sind. " +
            "Alle Daten bleiben auf deinem Handy.",
    ),
    GuideStep(
        R.drawable.ic_speed,
        "Fahrten",
        "Trag einmal im Monat ein, wie viele Kilometer du gefahren bist, und auf Wunsch den Verbrauch. " +
            "Die Diagramme zeigen dir den Verlauf.",
    ),
    GuideStep(
        R.drawable.ic_fuel,
        "Tanken",
        "Gib vom Tankbeleg die Liter und den Betrag ein. Den Preis pro Liter rechnet die App selbst aus.",
    ),
    GuideStep(
        R.drawable.ic_event,
        "Termine und Menü",
        "Im Menü oben rechts findest du die Termine für HU, Inspektion und Reifenwechsel, weitere Autos, " +
            "die Datensicherung als CSV-Datei und den Dunkelmodus.",
    ),
)

/**
 * Schnellstart, solange es noch kein Auto gibt: kurze Einführung, dann das erste Auto anlegen.
 * [onImport] lädt stattdessen eine vorhandene CSV-Datei.
 */
@Composable
fun Onboarding(onCreateCar: (CarInput) -> Unit, onImport: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val lastStep = GUIDE_STEPS.size // Danach kommt das Formular.

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (step < lastStep) {
                    TextButton(onClick = { step = lastStep }) { Text("Überspringen") }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "schritt",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { current ->
                if (current < lastStep) {
                    GuidePage(GUIDE_STEPS[current])
                } else {
                    FirstCarPage(onCreateCar, onImport)
                }
            }

            if (step < lastStep) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepDots(count = lastStep + 1, current = step, modifier = Modifier.weight(1f))
                    Button(onClick = { step++ }) { Text("Weiter") }
                }
            }
        }
    }
}

@Composable
private fun GuidePage(page: GuideStep) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(page.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            page.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun FirstCarPage(onCreateCar: (CarInput) -> Unit, onImport: () -> Unit) {
    val form = remember { CarFormState("", null, null) }
    var showErrors by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dein erstes Auto", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Gib ihm einen Namen. Kilometerstand und Baujahr kannst du auch später eintragen; " +
                "sie helfen bei den Erinnerungen an Inspektion und Zahnriemen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CarFields(form, cars = emptyList(), ownId = null, showErrors = showErrors)
        Button(
            onClick = { form.toInput(emptyList(), null)?.let(onCreateCar) ?: run { showErrors = true } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Los geht’s") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Daten aus CSV-Datei laden")
        }
        Text(
            "Zum Beispiel eine Sicherung, die du vorher im Menü heruntergeladen hast.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Schritt ${current + 1} von $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 10.dp else 8.dp)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Preview(name = "Schnellstart", showBackground = true, heightDp = 700)
@Composable
private fun OnboardingPreview() {
    KilometerTheme {
        Onboarding(onCreateCar = {}, onImport = {})
    }
}
