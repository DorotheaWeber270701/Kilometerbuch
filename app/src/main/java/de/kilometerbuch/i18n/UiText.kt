package de.kilometerbuch.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.kilometerbuch.ui.fmtDate
import de.kilometerbuch.ui.fmtWeekdayDate
import de.kilometerbuch.ui.monthLong
import java.time.LocalDate
import java.time.YearMonth

/**
 * Ein Text, der erst beim Anzeigen in der aktuellen Sprache zusammengesetzt wird. So können
 * Berechnungen (z. B. die Termine) Texte liefern, ohne selbst Sprachdateien zu brauchen.
 * Argumente vom Typ [YearMonth] und [LocalDate] werden dabei in der Sprache formatiert,
 * andere [UiText] aufgelöst, alles Übrige unverändert eingesetzt.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    /** Datum mit Wochentag, z. B. „Mi., 14.10.2026“. */
    data class WeekdayDate(val date: LocalDate) : UiText

    /** Mehrere Sätze, mit Leerzeichen verbunden. */
    data class Join(val parts: List<UiText>) : UiText
}

fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Mengenabhängiger Text; [count] ist auch das erste Argument (%d). */
fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = UiText.Plural(id, count, listOf(count) + args)

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.map { formatArg(context, it) }.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(id, count, *args.map { formatArg(context, it) }.toTypedArray())
    is UiText.WeekdayDate -> fmtWeekdayDate(date)
    is UiText.Join -> parts.joinToString(" ") { it.resolve(context) }
}

private fun formatArg(context: Context, arg: Any): Any = when (arg) {
    is UiText -> arg.resolve(context)
    is YearMonth -> monthLong(arg)
    is LocalDate -> fmtDate(arg)
    else -> arg
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)
