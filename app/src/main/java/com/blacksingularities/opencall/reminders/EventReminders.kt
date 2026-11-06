package com.blacksingularities.opencall.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import com.blacksingularities.opencall.MainActivity
import com.blacksingularities.opencall.R
import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.ContactEvent
import com.blacksingularities.opencall.data.ContactEvents
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.Format
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Przypomnienia o urodzinach i rocznicach (włączane w ustawieniach). Okresowe zadanie systemu
 * (co godzinę, przetrwa restart telefonu) sprawdza, czy minęła ustawiona godzina, i raz dziennie
 * pokazuje powiadomienie z dzisiejszymi okazjami. Bez stałej usługi w tle i bez dokładnych alarmów.
 */
object EventReminders {
    private const val JOB_ID = 7042
    const val CHANNEL = "contact_events"
    private const val NOTIFICATION_ID = 103
    private const val PREFS = "event_reminders"
    private const val KEY_DAY = "notified_day"

    /** Włącza albo wyłącza okresowe sprawdzanie zgodnie z ustawieniami. */
    fun schedule(context: Context, s: AppSettings) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!s.eventReminders) {
            js.cancel(JOB_ID)
            return
        }
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, EventReminderJob::class.java))
            .setPeriodic(60 * 60 * 1000L, 20 * 60 * 1000L)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(job) }
    }

    /** Po zmianie godziny albo włączeniu – dzisiejsze powiadomienie może pojawić się jeszcze raz. */
    fun resetToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DAY).apply()
    }

    /** Jedno sprawdzenie: po ustawionej godzinie, raz dziennie. */
    fun check(context: Context) {
        val app = context.applicationContext
        val s = SettingsStore(app).load()
        if (!s.eventReminders) return
        I18n.apply(s.language)
        val now = Calendar.getInstance()
        if (now.get(Calendar.HOUR_OF_DAY) < s.reminderHour) return
        val today = String.format(Locale.ROOT, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DAY, null) == today) return
        if (app.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return
        val events = ContactEvents.upcoming(app, 0, now)
        prefs.edit().putString(KEY_DAY, today).apply()
        if (events.isNotEmpty()) notify(app, events)
    }

    /** Opis okazji: „Urodziny · kończy 34 lata”, „Rocznica · 10. rocznica”. */
    fun describe(e: ContactEvent): String =
        listOfNotNull(Format.eventType(e.type, e.label), Format.countdown(e.date, e.birthday)?.substringAfter(" · ", "")?.takeIf { it.isNotBlank() })
            .joinToString(" · ")

    private fun notify(context: Context, events: List<ContactEvent>) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Urodziny i rocznice"), NotificationManager.IMPORTANCE_DEFAULT))
        val single = events.singleOrNull()
        val open = if (single != null) {
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, single.contactId))
        } else {
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_FAVORITES)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(context, 30, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_cake)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setShowWhen(false)
        if (single != null) {
            b.setContentTitle(if (single.birthday) tr("Dziś urodziny: {0}", single.name) else tr("Dziś: {0}", single.name))
            b.setContentText(describe(single))
        } else {
            b.setContentTitle(tr("Dzisiejsze okazje: {0}", events.size))
            b.setContentText(events.joinToString(", ") { it.name })
            val style = Notification.InboxStyle()
            events.take(6).forEach { style.addLine("${it.name} – ${describe(it)}") }
            b.setStyle(style)
        }
        runCatching { nm.notify(NOTIFICATION_ID, b.build()) }
    }
}

/** Zadanie okresowe: jedno sprawdzenie przypomnień w tle. */
class EventReminderJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            runCatching { EventReminders.check(this@EventReminderJob) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
