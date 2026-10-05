package de.kilometerbuch.i18n

import android.content.Context
import android.content.res.Configuration
import de.kilometerbuch.R
import java.util.Locale

/** Sprachen der App; [SYSTEM] folgt der Sprache des Handys. [nativeName] steht in der Auswahl. */
enum class AppLanguage(val tag: String?, val nativeName: String) {
    SYSTEM(null, ""),
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    PORTUGUESE("pt", "Português"),
    POLISH("pl", "Polski"),
    TURKISH("tr", "Türkçe"),
}

/**
 * Hält die aktuelle App-Sprache für Texte und Zahlenformate. [wrap] wird beim Start jeder
 * Activity und vor jeder Hintergrundprüfung aufgerufen; danach formatieren [locale] und die
 * Monatsnamen in der gewählten Sprache.
 */
object L10n {

    var locale: Locale = Locale.getDefault()
        private set

    private var monthsLong: List<String> = emptyList()
    private var monthsShort: List<String> = emptyList()

    /** Liefert einen Context mit der gewählten Sprache und stellt die Formatierung darauf um. */
    fun wrap(base: Context, language: AppLanguage): Context {
        val target = language.tag?.let(Locale::forLanguageTag) ?: base.resources.configuration.locales[0]
        locale = target
        Locale.setDefault(target)
        val config = Configuration(base.resources.configuration).apply { setLocale(target) }
        val localized = base.createConfigurationContext(config)
        monthsLong = localized.resources.getStringArray(R.array.months_long).toList()
        monthsShort = localized.resources.getStringArray(R.array.months_short).toList()
        return localized
    }

    /** Monatsname, z. B. „Oktober“; ohne geladene Sprache (z. B. in Tests) die Monatszahl. */
    fun monthName(month: Int): String = monthsLong.getOrNull(month - 1) ?: month.toString()

    fun monthShortName(month: Int): String = monthsShort.getOrNull(month - 1) ?: month.toString()
}
