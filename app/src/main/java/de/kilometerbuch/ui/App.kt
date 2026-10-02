package de.kilometerbuch.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kilometerbuch.R
import de.kilometerbuch.data.Car
import de.kilometerbuch.data.CarCare
import de.kilometerbuch.data.estimateOdometer
import de.kilometerbuch.data.Entry
import de.kilometerbuch.data.FuelReceipt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * [addLabel] ist die Beschriftung des Plus-Knopfs; null = kein Knopf auf dieser Seite.
 * Seiten mit [inBottomBar] = false erreicht man über das Menü.
 */
enum class Page(val label: String, val addLabel: String?, @DrawableRes val icon: Int, val inBottomBar: Boolean = true) {
    Trips("Fahrten", "Monat eintragen", R.drawable.ic_speed),
    Fuel("Tanken", "Tankbeleg eintragen", R.drawable.ic_fuel),
    Reminders("Termine", null, R.drawable.ic_event, inBottomBar = false),
}

/** Offener Bearbeiten-Dialog; [original] ist null für einen neuen Eintrag. */
private data class Editing<T>(val original: T?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel = viewModel()) {
    val cars by vm.cars.collectAsStateWithLifecycle()
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val cares by vm.cares.collectAsStateWithLifecycle()
    val snackText by vm.snack.collectAsStateWithLifecycle()
    val importReport by vm.importReport.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableStateOf(Page.Trips) }
    /** Wohin „Zurück“ von einer Menü-Seite führt. */
    var returnPage by rememberSaveable { mutableStateOf(Page.Trips) }
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
    /** Offener Einrichtungsdialog einer Erinnerung. */
    var editingCareItem by remember { mutableStateOf<Pair<Car, CareItem>?>(null) }

    // Benachrichtigungen: Zustand beim Zurückkehren in die App neu lesen (z. B. nach den Einstellungen).
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        onPauseOrDispose { }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    /** Fragt einmal per Systemdialog; danach geht es nur noch über die App-Einstellungen. */
    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications) {
            askedForNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

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
    BackHandler(enabled = !page.inBottomBar && !drawerState.isOpen) { page = returnPage }

    // Fällige Termine über alle Autos, für den Punkt am Menüsymbol und die Zahl im Menü.
    val today = LocalDate.now()
    val dueCount = carList.sumOf { car ->
        val care = cares.orEmpty().find { it.carId == car.id } ?: return@sumOf 0
        reminders(car, care, entries.orEmpty(), today).count { it.urgency != Urgency.LATER }
    }

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

    fun launchImport() = importLauncher.launch(
        arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"),
    )

    // Noch kein Auto: Schnellstart statt der leeren App.
    if (cars?.isEmpty() == true) {
        Onboarding(
            onCreateCar = { input ->
                vm.addCar(input.name, input.odometerKm, input.buildYear)?.let { carChoice = it.id }
                page = Page.Trips
            },
            onImport = ::launchImport,
        )
    } else Box(Modifier.fillMaxSize()) {
        // Das Menü soll von rechts kommen: Richtung für den Drawer umdrehen, für den Inhalt wieder zurück.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                drawerContent = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SideMenu(
                            themeMode = themeMode,
                            onThemeMode = vm::setThemeMode,
                            cars = carList,
                            dueCount = dueCount,
                            onReminders = {
                                closeMenu()
                                if (page.inBottomBar) returnPage = page
                                page = Page.Reminders
                            },
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
                                launchImport()
                            },
                        )
                    }
                },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                navigationIcon = {
                                    if (!page.inBottomBar) {
                                        IconButton(onClick = { page = returnPage }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                                        }
                                    }
                                },
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
                            if (page.inBottomBar) {
                                NavigationBar {
                                    Page.entries.filter { it.inBottomBar }.forEach { p ->
                                        NavigationBarItem(
                                            selected = p == page,
                                            onClick = { page = p },
                                            icon = { Icon(painterResource(p.icon), contentDescription = null) },
                                            label = { Text(p.label) },
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            val addLabel = page.addLabel
                            if (addLabel != null && defaultCarId != null && entries != null && receipts != null) {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        when (page) {
                                            Page.Trips -> editingEntry = Editing(null)
                                            Page.Fuel -> editingReceipt = Editing(null)
                                            Page.Reminders -> Unit
                                        }
                                    },
                                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                    text = { Text(addLabel) },
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
                        val careList = cares
                        when {
                            carList.isEmpty() || tripList == null || receiptList == null || careList == null ->
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

                            page == Page.Reminders -> RemindersContent(
                                cars = carList,
                                cares = careList,
                                entries = tripList,
                                selectedCar = selectedCar,
                                contentPadding = contentPadding,
                                notificationsAllowed = notificationsAllowed,
                                onAllowNotifications = ::allowNotifications,
                                onToggle = { car, careItem, on ->
                                    val care = careList.find { it.carId == car.id } ?: CarCare(car.id)
                                    // Einschalten fragt erst nach fehlenden Angaben; Ausschalten geht sofort.
                                    if (on && needsSetup(careItem, care, car)) {
                                        editingCareItem = car to careItem
                                    } else {
                                        vm.setCareItemOn(car.id, careItem, on)
                                    }
                                },
                                onEdit = { car, careItem -> editingCareItem = car to careItem },
                                onDone = vm::markDone,
                                onSetHuAppointment = vm::setHuAppointment,
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
                Box {
                    MenuCloseIcon(
                        progress = menuProgress,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Dezenter Hinweis auf fällige Termine, solange das Menü zu ist.
                    if (dueCount > 0 && menuProgress < 0.5f) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-1).dp)
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                    }
                }
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
        val original = state.original
        val currentOdometer = original?.let { estimateOdometer(it, entries.orEmpty()) }
        CarDialog(
            original = original,
            cars = carList,
            currentOdometer = currentOdometer,
            onDismiss = { editingCar = null },
            onSave = { input ->
                if (original == null) {
                    vm.addCar(input.name, input.odometerKm, input.buildYear)?.let { carChoice = it.id }
                } else {
                    // Nur ein geänderter Kilometerstand gilt als neu abgelesen.
                    val odometerChanged = input.odometerKm != currentOdometer
                    vm.updateCar(
                        original.copy(
                            name = input.name,
                            odometerKm = if (odometerChanged) input.odometerKm else original.odometerKm,
                            odometerMonth = if (odometerChanged) input.odometerKm?.let { YearMonth.now() } else original.odometerMonth,
                            buildYear = input.buildYear,
                        ),
                    )
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

    editingCareItem?.let { (car, careItem) ->
        CareItemDialog(
            car = car,
            care = cares.orEmpty().find { it.carId == car.id } ?: CarCare(car.id),
            item = careItem,
            onDismiss = { editingCareItem = null },
            onSave = { care, updatedCar ->
                if (updatedCar != car) vm.updateCar(updatedCar)
                vm.saveCare(care)
                editingCareItem = null
                // Beim ersten Einrichten gleich nach der Erlaubnis fragen, damit die Erinnerungen ankommen.
                if (!notificationsAllowed && !askedForNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    allowNotifications()
                }
            },
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
