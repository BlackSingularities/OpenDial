package com.blacksingularities.opencall.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import com.blacksingularities.opencall.MainActivity
import com.blacksingularities.opencall.R
import com.blacksingularities.opencall.i18n.tr

/** Powiadomienia rozmów: dzwoni (pełny ekran), trwa (powrót / rozłącz), nieodebrane i zablokowane. */
object CallNotifications {
    const val CH_INCOMING = "incoming_calls"
    const val CH_ONGOING = "ongoing_calls"
    const val CH_MISSED = "missed_calls"
    const val CH_BLOCKED = "blocked_calls"
    const val ID_CALL = 100
    const val ID_MISSED = 101
    const val ID_BLOCKED = 102

    private var lastKey = ""

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_INCOMING, tr("Połączenia przychodzące"), NotificationManager.IMPORTANCE_HIGH).apply {
                // Dzwonek odtwarza Telecom – kanał musi być cichy, inaczej dźwięki by się nałożyły.
                setSound(null, null as AudioAttributes?)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ONGOING, tr("Trwające połączenia"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null as AudioAttributes?)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(NotificationChannel(CH_MISSED, tr("Nieodebrane połączenia"), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_BLOCKED, tr("Zablokowane połączenia"), NotificationManager.IMPORTANCE_LOW))
    }

    private fun flags() = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun action(context: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(context, req, Intent(context, CallActionReceiver::class.java).setAction(action), flags())

    /** Odświeża powiadomienie bieżącego połączenia (wywoływane przy każdej zmianie stanu). */
    fun update(context: Context, silent: Boolean = false) {
        val list = CallManager.snapshot()
        val c = CallManager.primary(list)
        if (c == null) {
            cancel(context)
            return
        }
        val key = "${c.state}|${c.title}|${list.size}|${c.connectTime}|$silent"
        if (key == lastKey) return
        lastKey = key
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(context, 1, CallActivity.intent(context), flags())
        val title = c.title.ifBlank { tr("Numer prywatny") }
        val person = Person.Builder().setName(title).setImportant(true).apply {
            if (c.number.isNotBlank()) setUri("tel:${c.number}")
            c.photoUri?.let { runCatching { setIcon(Icon.createWithContentUri(Uri.parse(it))) } }
        }.build()

        val n = if (c.ringing) {
            val answer = PendingIntent.getActivity(context, 2, CallActivity.intent(context, answer = true), flags())
            val decline = action(context, CallActionReceiver.ACTION_DECLINE, 3)
            val b = Notification.Builder(context, CH_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(title)
                .setContentText(if (list.size > 1) tr("Połączenie oczekujące") else tr("Połączenie przychodzące"))
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            if (Build.VERSION.SDK_INT >= 31) {
                b.setStyle(Notification.CallStyle.forIncomingCall(person, decline, answer))
            } else {
                b.addAction(Notification.Action.Builder(null, tr("Odrzuć"), decline).build())
                b.addAction(Notification.Action.Builder(null, tr("Odbierz"), answer).build())
            }
            b.build()
        } else {
            val hangup = action(context, CallActionReceiver.ACTION_HANGUP, 4)
            val text = when {
                c.dialing -> tr("Łączenie…")
                c.held -> tr("Zawieszone")
                list.size > 1 -> tr("{0} połączenia", list.size)
                else -> tr("Trwa rozmowa")
            }
            val b = Notification.Builder(context, CH_ONGOING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
            if (c.active && c.connectTime > 0) {
                b.setUsesChronometer(true).setWhen(c.connectTime).setShowWhen(true)
            }
            if (Build.VERSION.SDK_INT >= 31 && !plainOngoing) {
                b.setStyle(Notification.CallStyle.forOngoingCall(person, hangup))
            } else {
                b.addAction(Notification.Action.Builder(null, tr("Rozłącz"), hangup).build())
            }
            b.build()
        }
        if (!post(nm, n) && !c.ringing && !plainOngoing) {
            // Styl połączenia odrzucony – ten sam stan jeszcze raz jako zwykłe powiadomienie.
            plainOngoing = true
            lastKey = ""
            update(context, silent)
        }
    }

    /** Ustawiane, gdy system nie przyjął stylu połączenia dla trwającej rozmowy. */
    private var plainOngoing = false

    /**
     * Styl połączenia wymaga usługi pierwszoplanowej (rozmowa trwa) albo intencji pełnoekranowej
     * (dzwoni). InCallService jest awansowana do pierwszego planu; gdy to się nie uda – zwykłe powiadomienie.
     */
    private fun post(nm: NotificationManager, n: Notification): Boolean {
        val svc = CallManager.service
        if (svc != null && Build.VERSION.SDK_INT >= 30) {
            val fg = runCatching {
                svc.startForeground(ID_CALL, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            }.isSuccess
            if (fg) return true
        }
        return runCatching { nm.notify(ID_CALL, n) }.isSuccess
    }

    fun cancel(context: Context) {
        lastKey = ""
        runCatching { CallManager.service?.stopForeground(android.app.Service.STOP_FOREGROUND_REMOVE) }
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_CALL)
    }

    /** Nieodebrane: jedno zbiorcze powiadomienie z akcjami „Oddzwoń” i „SMS”. */
    fun missed(context: Context, count: Int, number: String?, name: String?) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_RECENTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags(),
        )
        val clear = action(context, CallActionReceiver.ACTION_MISSED_SEEN, 11)
        val who = name ?: number?.takeIf { it.isNotBlank() } ?: tr("Numer prywatny")
        val b = Notification.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_missed)
            .setContentTitle(if (count > 1) tr("Nieodebrane połączenia: {0}", count) else tr("Nieodebrane połączenie"))
            .setContentText(if (count > 1) tr("Ostatnie: {0}", who) else who)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setDeleteIntent(clear)
            .setAutoCancel(true)
            .setShowWhen(true)
        if (!number.isNullOrBlank()) {
            val callBack = PendingIntent.getBroadcast(
                context, 12,
                Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_CALL_BACK).putExtra(CallActionReceiver.EXTRA_NUMBER, number),
                flags(),
            )
            val sms = PendingIntent.getActivity(
                context, 13,
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                flags(),
            )
            b.addAction(Notification.Action.Builder(null, tr("Oddzwoń"), callBack).build())
            b.addAction(Notification.Action.Builder(null, tr("Wiadomość"), sms).build())
        }
        runCatching { nm.notify(ID_MISSED, b.build()) }
    }

    fun cancelMissed(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_MISSED)
    }

    fun blocked(context: Context, number: String, reason: String) {
        channels(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 20,
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SHOW_BLOCKED).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags(),
        )
        val n = Notification.Builder(context, CH_BLOCKED)
            .setSmallIcon(R.drawable.ic_stat_block)
            .setContentTitle(tr("Zablokowano połączenie"))
            .setContentText("${number.ifBlank { tr("Numer prywatny") }} · $reason")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID_BLOCKED, n) }
    }
}
