package de.kilometerbuch.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.kilometerbuch.MainActivity
import de.kilometerbuch.R
import de.kilometerbuch.data.CarRepository
import de.kilometerbuch.data.CareRepository
import de.kilometerbuch.data.EntryRepository
import de.kilometerbuch.ui.Urgency
import de.kilometerbuch.ui.reminders
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Prüft einmal am Tag alle Termine und benachrichtigt, wenn einer bald fällig oder überfällig ist.
 * Jeder Termin meldet sich pro Stufe nur einmal.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    @SuppressLint("MissingPermission") // geprüft über areNotificationsEnabled()
    override fun doWork(): Result {
        val ctx = applicationContext
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return Result.success()

        val cars = CarRepository(ctx).load()
        if (cars.isEmpty()) return Result.success()
        val cares = CareRepository(ctx).load()
        val entries = EntryRepository(ctx).load(cars.first().id)
        val today = LocalDate.now()

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet(KEY_SENT, emptySet()).orEmpty().toMutableSet()
        ensureChannel(ctx)

        cares.forEach { care ->
            val car = cars.find { it.id == care.carId } ?: return@forEach
            reminders(car, care, entries, today)
                .forEach { reminder ->
                    val appointment = reminder.appointment
                    val key: String
                    val title: String
                    if (appointment != null) {
                        // Gebuchter Termin: nur am Tag vorher (bzw. am selben Tag) erinnern.
                        val days = ChronoUnit.DAYS.between(today, appointment)
                        if (days !in 0..1) return@forEach
                        key = "${car.id}/HU-termin/$appointment"
                        title = "${car.name}: TÜV-Termin ${if (days == 0L) "heute" else "morgen"}"
                    } else {
                        if (reminder.urgency == Urgency.LATER) return@forEach
                        key = "${reminder.key}/${reminder.urgency}"
                        title = "${car.name}: ${reminder.kind.title}" +
                            if (reminder.urgency == Urgency.OVERDUE) " überfällig" else ""
                    }
                    if (key in sent) return@forEach
                    val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_car)
                        .setContentTitle(title)
                        .setContentText(reminder.headline)
                        .setStyle(NotificationCompat.BigTextStyle().bigText("${reminder.headline}\n${reminder.detail}"))
                        .setContentIntent(openAppIntent(ctx))
                        .setAutoCancel(true)
                        .build()
                    manager.notify(key.hashCode(), notification)
                    sent += key
                }
        }
        prefs.edit().putStringSet(KEY_SENT, sent).apply()
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "termine"
        private const val PREFS = "benachrichtigungen"
        private const val KEY_SENT = "gesendet"
        private const val WORK_NAME = "termine-taeglich"

        /** Plant die tägliche Prüfung; bleibt bestehen, wenn sie schon geplant ist. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Termine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Erinnerungen an HU, Inspektion und Reifenwechsel"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
