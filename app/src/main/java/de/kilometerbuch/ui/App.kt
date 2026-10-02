package de.kilometerbuch.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.FuelReceipt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class Page(val label: String, val addLabel: String, @DrawableRes val icon: Int) {
    Trips("Fahrten", "Monat eintragen", R.drawable.ic_speed),
    Fuel("Tanken", "Tankbeleg eintragen", R.drawable.ic_fuel),
}

/** Offener Bearbeiten-Dialog; [original] ist null für einen neuen Eintrag. */
private data class Editing<T>(val original: T?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val cars by vm.cars.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val snackText by vm.snack.collectAsStateWithLifecycle()
    val importReport by vm.importReport.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableStateOf(Page.Trips) }
    var year by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }
    var range by rememberSaveable { mutableStateOf(ChartRange.M12) }
    /** Id des gewählten Autos, [ALL_CARS] für die Gesamtansicht, null = noch nichts gewählt. */
    var carChoice by rememberSaveable { mutableStateOf<String?>(null) }

    var editingEntry by remember { mutableStateOf<Editing<Entry>?>(null) }
    var editingReceipt by remember { mutableStateOf<Editing<FuelReceipt>?>(null) }
    var editingCar by remember { mutableStateOf<Editing<Car>?>(null) }
    var deletingEntry by remember { mutableStateOf<Entry?>(null) }
    var deletingReceipt by remember { mutableStateOf<FuelReceipt?>(null) }
    var deletingCar by remember { mutableStateOf<Car?>(null) }

    val carList = cars.orEmpty()
    val showAll = carChoice == ALL_CARS
    val selectedCar: Car? = if (showAll) null else carList.find { it.id == carChoice } ?: carList.firstOrNull()
    /** Vorbelegung in Dialogen: das gewählte Auto, in der Gesamtansicht das erste. */
    val defaultCarId = selectedCar?.id ?: carList.firstOrNull()?.id

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val menuProgress by animateFloatAsState(
        targetValue = if (drawerState.targetValue == DrawerValue.Open) 1f else 0f,
        animationSpec = tween(300),
        label = "menu",
    )
    fun closeMenu() = scope.launch { drawerState.close() }
    BackHandler(enabled = drawerState.isOpen) { closeMenu() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(snackText) {
        snackText?.let {
            snackbar.showSnackbar(it)
            vm.snackShown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Das Menü soll von rechts kommen: Richtung für den Drawer umdrehen, für den Inhalt wieder zurück.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SideMenu(
                            cars = carList,
                            onEditCar = {
                                closeMenu()
                                editingCar = Editing(it)
                            },
                            onAddCar = {
                                closeMenu()
                                editingCar = Editing(null)
                            },
                            onExport = {
                                closeMenu()
                                exportLauncher.launch("kilometerbuch-${LocalDate.now()}.csv")
                            },
                            onImport = {
                                closeMenu()
                                importLauncher.launch(
                                    arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"),
                                )
                            },
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    if (carList.isNotEmpty()) {
                                        CarSelector(
                                            cars = carList,
                                            selectedCar = selectedCar,
                                            onSelect = { carChoice = it },
                                            onAddCar = { editingCar = Editing(null) },
                                        )
                                    }
                                },
                                // Platzhalter: das Menüsymbol liegt darüber, damit es auch über dem offenen Menü sichtbar bleibt.
                                actions = { Spacer(Modifier.width(48.dp)) },
                            )
                        },
                        bottomBar = {
                            NavigationBar {
                                Page.entries.forEach { p ->
                                    NavigationBarItem(
                                        selected = p == page,
                                        onClick = { page = p },
                                        icon = { Icon(painterResource(p.icon), contentDescription = null) },
                                        label = { Text(p.label) },
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            if (defaultCarId != null && entries != null && receipts != null) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        when (page) {
                                            Page.Trips -> editingEntry = Editing(null)
                                            Page.Fuel -> editingReceipt = Editing(null)
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    text = { Text(page.addLabel) },
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(snackbar) },
                    ) { padding ->
                        val contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        )
                        val tripList = entries
                        val receiptList = receipts
                        when {
                            carList.isEmpty() || tripList == null || receiptList == null ->
                                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }

                            page == Page.Trips -> TripsContent(
                                cars = carList,
                                entries = tripList,
                                selectedCar = selectedCar,
                                year = year,
                                onYearChange = { year = it },
                                range = range,
                                onRangeChange = { range = it },
                                contentPadding = contentPadding,
                                onEdit = { editingEntry = Editing(it) },
                            )

                            else -> FuelContent(
                                cars = carList,
                                receipts = receiptList,
                                selectedCar = selectedCar,
                                year = year,
                                onYearChange = { year = it },
                                range = range,
                                onRangeChange = { range = it },
                                contentPadding = contentPadding,
                                onEdit = { editingReceipt = Editing(it) },
                            )
                        }
                    }
                }
            }
        }

        // Menüsymbol oben rechts, über allem; wird beim Öffnen zum X.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 4.dp)
                .height(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }) {
                MenuCloseIcon(
                    progress = menuProgress,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier,
                )
            }
        }
    }

    // --- Dialoge ---

    val carForDialogs = defaultCarId
    editingEntry?.let { state ->
        if (carForDialogs != null) {
            EntryDialog(
                original = state.original,
                cars = carList,
                initialCarId = carForDialogs,
                existing = entries.orEmpty(),
                onDismiss = { editingEntry = null },
                onSave = { entry ->
                    vm.saveEntry(entry, replacing = state.original)
                    editingEntry = null
                },
                onDelete = {
                    deletingEntry = state.original
                    editingEntry = null
                },
            )
        }
    }

    editingReceipt?.let { state ->
        if (carForDialogs != null) {
            ReceiptDialog(
                original = state.original,
                cars = carList,
                initialCarId = carForDialogs,
                onDismiss = { editingReceipt = null },
                onSave = { receipt ->
                    vm.saveReceipt(receipt)
                    editingReceipt = null
                },
                onDelete = {
                    deletingReceipt = state.original
                    editingReceipt = null
                },
            )
        }
    }

    editingCar?.let { state ->
        CarDialog(
            original = state.original,
            cars = carList,
            onDismiss = { editingCar = null },
            onSave = { name ->
                val original = state.original
                if (original == null) {
                    vm.addCar(name)?.let { carChoice = it.id }
                } else {
                    vm.renameCar(original.id, name)
                }
                editingCar = null
            },
            onDelete = {
                deletingCar = state.original
                editingCar = null
            },
        )
    }

    deletingEntry?.let { entry ->
        ConfirmDeleteDialog(
            title = "Eintrag löschen?",
            text = "${monthLong(entry.month)} mit ${fmtInt(entry.km.toDouble())} km wird gelöscht.",
            onConfirm = {
                vm.deleteEntry(entry)
                deletingEntry = null
            },
            onDismiss = { deletingEntry = null },
        )
    }

    deletingReceipt?.let { receipt ->
        ConfirmDeleteDialog(
            title = "Tankbeleg löschen?",
            text = "Beleg vom ${fmtDate(receipt.date)} über ${fmt2(receipt.total)} € wird gelöscht.",
            onConfirm = {
                vm.deleteReceipt(receipt.id)
                deletingReceipt = null
            },
            onDismiss = { deletingReceipt = null },
        )
    }

    deletingCar?.let { car ->
        val trips = entries.orEmpty().count { it.carId == car.id }
        val fuel = receipts.orEmpty().count { it.carId == car.id }
        ConfirmDeleteDialog(
            title = "„${car.name}“ löschen?",
            text = "Das Auto wird mit $trips Fahrten und $fuel Tankbelegen gelöscht. " +
                "Lade vorher eine CSV-Datei herunter, wenn du die Daten behalten willst.",
            onConfirm = {
                vm.deleteCar(car.id)
                if (carChoice == car.id) carChoice = null
                deletingCar = null
            },
            onDismiss = { deletingCar = null },
        )
    }

    importReport?.let { report ->
        AlertDialog(
            onDismissRequest = vm::importReportShown,
            title = { Text("CSV hochladen") },
            text = { Text(report) },
            confirmButton = { TextButton(onClick = vm::importReportShown) { Text("OK") } },
        )
    }
}
