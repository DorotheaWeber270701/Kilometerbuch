package de.kilometerbuch.ui.theme

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.kilometerbuch.R
import de.kilometerbuch.data.Car

/** Hell oder dunkel, wie in der App gewählt; nicht unbedingt wie im System. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Darstellung, wie im Menü gewählt. */
enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun KilometerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = Color(0xFF8AB4F2))
        else -> lightColorScheme(primary = Color(0xFF1F62B4))
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// Feste Autofarben in fester Reihenfolge, für hellen und dunklen Hintergrund getrennt abgestimmt.
private val CAR_COLORS_LIGHT = listOf(
    0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF4A3AA7, 0xFFE34948,
)
private val CAR_COLORS_DARK = listOf(
    0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767,
)

/** Palettenfarbe [index], passend zu hell oder dunkel. */
@Composable
@ReadOnlyComposable
fun paletteColor(index: Int): Color {
    val palette = if (LocalDarkTheme.current) CAR_COLORS_DARK else CAR_COLORS_LIGHT
    return Color(palette[index.mod(palette.size)])
}

/** Farbe eines Autos: die eigene Farbe, sonst seine Palettenfarbe. */
@Composable
@ReadOnlyComposable
fun carColor(car: Car): Color = car.customColor?.let { Color(it) } ?: paletteColor(car.colorIndex)

/** Für Werte über alle Autos zusammen, damit sie keinem Auto zugeordnet wirken. */
@Composable
fun allCarsColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant
