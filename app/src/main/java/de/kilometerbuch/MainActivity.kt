package de.kilometerbuch

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kilometerbuch.data.Settings
import de.kilometerbuch.i18n.L10n
import de.kilometerbuch.notify.ReminderWorker
import de.kilometerbuch.ui.App
import de.kilometerbuch.ui.MainViewModel
import de.kilometerbuch.ui.theme.KilometerTheme
import de.kilometerbuch.ui.theme.isDark

class MainActivity : ComponentActivity() {

    /** Die App-Sprache gilt für die ganze Activity; ein Wechsel baut sie mit recreate() neu auf. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(L10n.wrap(newBase, Settings(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderWorker.schedule(this)
        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark()

            // Symbole in Status- und Navigationsleiste passend zur gewählten Darstellung, nicht zum System.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }

            KilometerTheme(darkTheme = dark) {
                App(vm)
            }
        }
    }
}
