package de.kilometerbuch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.i18n.AppLanguage
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
    language: AppLanguage,
    onLanguage: () -> Unit,
    onPrivacy: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(64.dp).padding(start = 28.dp), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.menu), style = MaterialTheme.typography.titleLarge)
            }

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.page_reminders)) },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null) },
                badge = {
                    if (dueCount > 0) {
                        Text(
                            stringResource(R.string.due_count, dueCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                onClick = onReminders,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel(stringResource(R.string.cars_section))
            cars.forEach { car ->
                NavigationDrawerItem(
                    label = { Text(car.name) },
                    selected = false,
                    icon = { ColorDot(carColor(car)) },
                    badge = { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_car_named, car.name)) },
                    onClick = { onEditCar(car) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.add_car)) },
                selected = false,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = onAddCar,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel(stringResource(R.string.data_section))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.csv_download)) },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_download), contentDescription = null) },
                onClick = onExport,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.csv_upload)) },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_upload), contentDescription = null) },
                onClick = onImport,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            Text(
                stringResource(R.string.csv_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
            )

            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))

            SectionLabel(stringResource(R.string.appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 8.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { onThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                    ) { Text(stringResource(mode.label)) }
                }
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.language)) },
                selected = false,
                icon = { Icon(painterResource(R.drawable.ic_language), contentDescription = null) },
                badge = { Text(languageLabel(language), style = MaterialTheme.typography.labelLarge) },
                onClick = onLanguage,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.privacy_policy)) },
                selected = false,
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                onClick = onPrivacy,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String =
    if (language == AppLanguage.SYSTEM) stringResource(R.string.language_system) else language.nativeName

/** Sprachwahl; jede Sprache steht in ihrer eigenen Schreibweise, damit man sie immer findet. */
@Composable
fun LanguageDialog(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == current, onClick = { onSelect(language) })
                        Text(languageLabel(language), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
