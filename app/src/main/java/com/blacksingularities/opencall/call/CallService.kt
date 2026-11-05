package com.blacksingularities.opencall.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Usługa wiązana przez Telecom, gdy OpenDial jest domyślną aplikacją telefonu: przekazuje
 * połączenia do [CallManager] i otwiera ekran rozmowy (przy dzwonieniu – przez powiadomienie
 * pełnoekranowe, żeby działało też przy zablokowanym ekranie).
 */
class CallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        CallManager.attach(this)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(this, call)
        // Połączenie alarmowe z systemowego ekranu awaryjnego też otwiera okno oddzwaniania.
        val d = call.details
        if (d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) ||
            (d.callDirection == Call.Details.DIRECTION_OUTGOING && Dialer.isEmergency(this, CallManager.numberOf(call)))
        ) {
            Dialer.markEmergency(this)
        }
        if (call.details.state != Call.STATE_RINGING) {
            // Połączenie wychodzące – ekran rozmowy od razu.
            startActivity(CallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
    }

    @Deprecated("Zastąpione przez onCallEndpointChanged w API 34, ale nadal wywoływane")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallManager.audioChanged(audioState)
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        startActivity(CallActivity.intent(this, showDialpad = showDialpad).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        CallNotifications.update(this, silent = true)
    }

    override fun onDestroy() {
        CallNotifications.cancel(this)
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }
}
