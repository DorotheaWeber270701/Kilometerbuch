package de.kilometerbuch.data

import android.content.Context
import de.kilometerbuch.i18n.AppLanguage
import de.kilometerbuch.ui.theme.ThemeMode

/** Kleine App-Einstellungen, die nicht zu den Fahrdaten gehören. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    /** commit statt apply: Direkt danach wird die Ansicht neu aufgebaut und liest die Sprache wieder. */
    var language: AppLanguage
        get() = prefs.getString(KEY_LANGUAGE, null)?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value.name).commit()
        }

    private companion object {
        const val KEY_THEME = "darstellung"
        const val KEY_LANGUAGE = "sprache"
    }
}
