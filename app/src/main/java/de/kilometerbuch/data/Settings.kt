package de.kilometerbuch.data

import android.content.Context
import de.kilometerbuch.ui.theme.ThemeMode

/** Kleine App-Einstellungen, die nicht zu den Fahrdaten gehören. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    private companion object {
        const val KEY_THEME = "darstellung"
    }
}
