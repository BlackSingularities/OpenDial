package com.blacksingularities.opencall.call

import android.Manifest
import android.os.Build
import android.provider.CallLog
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.blacksingularities.opencall.ai.AiHandoff
import com.blacksingularities.opencall.ai.AiLog
import com.blacksingularities.opencall.ai.AiMatch
import com.blacksingularities.opencall.ai.AiRules
import com.blacksingularities.opencall.ai.AiScreening
import com.blacksingularities.opencall.ai.AiSync
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.tr
import java.util.Calendar

/**
 * Filtr połączeń przychodzących (domyślny dialer dostaje go dla wszystkich połączeń): numery
 * ukryte, nieznane, reguły prefiksów („0700”, „+48 800*”). Systemowa lista blokad działa
 * niezależnie – Telecom odrzuca te numery, zanim w ogóle trafią tutaj.
 *
 * Asystent AI: aplikacja bez uprawnień systemowych nie ma dostępu do dźwięku rozmowy GSM, więc
 * rozmawia agent w chmurze. Telefon tylko decyduje: odrzuca połączenie (operator przekierowuje
 * „przy zajętym” na numer agenta) albo wycisza je (przekierowanie „gdy nie odbiera”).
 */
class ScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        // Po połączeniu alarmowym służby mogą oddzwonić z dowolnego (także ukrytego) numeru:
        // żadnych blokad, wyciszania ani przekazywania do asystenta.
        if (Dialer.recentEmergency(this)) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val s = SettingsStore(this).load()
        val number = details.handle?.schemeSpecificPart ?: ""
        val hidden = number.isBlank() ||
            (Build.VERSION.SDK_INT >= 30 && details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED)

        // Agent przełącza rozmowę ze swojego numeru. Odrzucenie zapętliłoby ją z powrotem do niego.
        if (s.ai.enabled && !hidden && PhoneText.sameNumber(number, s.ai.agentNumber)) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val reason: String? = when {
            hidden && s.blockHidden -> tr("numer ukryty")
            !hidden && s.blockRules.any { PhoneText.matchesRule(number, it) } -> tr("reguła blokowania")
            else -> null
        }
        val canCheckContacts = Dialer.has(this, Manifest.permission.READ_CONTACTS)
        val unknown = !hidden && (s.blockUnknown || s.silenceUnknown || (s.ai.ready && canCheckContacts)) &&
            runCatching { ContactsRepo(this).lookup(number) == null }.getOrDefault(false)
        val toAgent = reason == null && canCheckContacts && unknownForAgent(s.ai, number, hidden, unknown)

        val response = CallResponse.Builder()
        when {
            toAgent -> {
                if (s.ai.handoff == AiHandoff.BUSY) {
                    response.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(!s.logBlocked)
                } else {
                    response.setSilenceCall(true)
                }
                runCatching { AiSync.handoff(this, number, hidden, announce = s.ai.handoff == AiHandoff.BUSY) }
            }
            reason != null || (unknown && s.blockUnknown) -> {
                response.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(!s.logBlocked)
                if (s.notifyBlocked) CallNotifications.blocked(this, number, reason ?: tr("spoza kontaktów"))
            }
            unknown && s.silenceUnknown -> response.setSilenceCall(true)
        }
        respondToCall(details, response.build())
    }

    private fun unknownForAgent(ai: AiScreening, number: String, hidden: Boolean, unknown: Boolean): Boolean {
        if (!ai.ready) return false
        val now = System.currentTimeMillis()
        return AiRules.shouldScreen(
            a = ai,
            hidden = hidden,
            unknown = unknown,
            repeated = runCatching { AiMatch.isRepeat(AiLog(this).load(), number, ai.repeatMinutes, now) }.getOrDefault(false),
            calledRecently = !hidden && calledRecently(number, ai.recentOutgoingDays, now),
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            roaming = runCatching { getSystemService(TelephonyManager::class.java)?.isNetworkRoaming == true }.getOrDefault(false),
        )
    }

    /** Oddzwonienie po Twoim telefonie (kurier, serwis) nie powinno trafiać do asystenta. */
    private fun calledRecently(number: String, days: Int, now: Long): Boolean {
        if (days <= 0 || !Dialer.has(this, Manifest.permission.READ_CALL_LOG)) return false
        return runCatching {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), (now - days * 86_400_000L).toString()),
                null,
            )?.use { c ->
                var found = false
                while (!found && c.moveToNext()) found = PhoneText.sameNumber(c.getString(0) ?: "", number)
                found
            } ?: false
        }.getOrDefault(false)
    }
}
