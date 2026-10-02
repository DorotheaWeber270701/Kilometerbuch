package de.kilometerbuch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.ui.theme.ThemeMode
import de.kilometerbuch.ui.theme.carColor

/** Inhalt des Seitenmenüs rechts. Oben bleibt Platz für das X, das über dem Menü liegt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideMenu(
    cars: List<Car>,
    dueCount: Int,
    onReminders: () -> Unit,
    onEditCar: (Car) -> Unit,
    onAddCar: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(start = 28.dp), contentAlignment = Alignment.CenterStart) {
                Text("Menü", style = MaterialTheme.typography.titleLarge)
            }

            NavigationDrawerItem(
                label = { Text("Termine") },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null) },
                badge = {
                    if (dueCount > 0) {
                        Text(
                            "$dueCount fällig",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                onClick = onReminders,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel("Autos")
            cars.forEach { car ->
                NavigationDrawerItem(
                    label = { Text(car.name) },
                    selected = false,
                    icon = { ColorDot(carColor(car.colorIndex)) },
                    badge = { Icon(Icons.Filled.Edit, contentDescription = "${car.name} bearbeiten") },
                    onClick = { onEditCar(car) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            NavigationDrawerItem(
                label = { Text("Auto hinzufügen") },
                selected = false,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onAddCar,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel("Daten")
            NavigationDrawerItem(
                label = { Text("Als CSV herunterladen") },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                onClick = onExport,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("CSV hochladen") },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_upload), contentDescription = null) },
                onClick = onImport,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            Text(
                "Die Datei enthält alle Autos, Fahrten und Tankbelege und lässt sich mit Excel öffnen. " +
                    "Beim Hochladen werden die Einträge ergänzt: Ein schon vorhandener Monat wird ersetzt, " +
                    "doppelte Tankbelege werden übersprungen. Autos werden über ihren Namen zugeordnet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel("Darstellung")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 24.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 8.dp),
    )
}
