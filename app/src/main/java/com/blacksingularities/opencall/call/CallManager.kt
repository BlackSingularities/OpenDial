package com.blacksingularities.opencall.call

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.VideoProfile
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Migawka połączenia dla interfejsu (Call z Telecom zmienia się w miejscu – UI dostaje kopię). */
data class CallView(
    val call: Call,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val label: String?,
    val contactId: Long?,
    val state: Int,
    val connectTime: Long,
    val conference: Boolean,
    val children: Int,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    val canMute: Boolean,
    val video: Boolean,
    val hidden: Boolean,
    val accountLabel: String?,
    val disconnect: DisconnectCause?,
) {
    val title: String get() = name ?: number.ifBlank { "" }
    val ringing: Boolean get() = state == Call.STATE_RINGING
    val active: Boolean get() = state == Call.STATE_ACTIVE
    val held: Boolean get() = state == Call.STATE_HOLDING
    val dialing: Boolean get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_PULLING_CALL
    val ended: Boolean get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
}

/**
 * Stan wszystkich połączeń z InCallService. Ekran rozmowy i powiadomienia obserwują [tick]
 * i pobierają aktualne migawki przez [snapshot].
 */
@SuppressLint("StaticFieldLeak")
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val calls = ArrayList<Call>()
    private val callers = ConcurrentHashMap<String, ContactsRepo.CallerId>()
    private val wasActive = HashSet<Call>()
    private var sims: List<com.blacksingularities.opencall.data.SimAccount> = emptyList()

    var service: CallService? = null
        internal set
    private var appContext: Context? = null

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    private val _audio = MutableStateFlow<CallAudioState?>(null)
    val audio: StateFlow<CallAudioState?> = _audio

    /** Ostatnio zakończone połączenie – ekran pokazuje je chwilę z przyczyną rozłączenia. */
    var lastEnded: CallView? = null
        private set

    /** Cyfry wysłane w trakcie rozmowy (np. wybory w infolinii) – widoczne nad klawiaturą. */
    var dtmfHistory = ""
        private set

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_ACTIVE && wasActive.add(call)) onConnected()
            if (state == Call.STATE_DISCONNECTED) onDisconnected(call)
            changed()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = changed()
        override fun onParentChanged(call: Call, parent: Call?) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = changed()
        override fun onCallDestroyed(call: Call) = changed()
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    internal fun add(context: Context, call: Call) {
        attach(context)
        if (calls.contains(call)) return
        if (calls.isEmpty()) sims = Dialer.sims(context)
        calls.add(call)
        call.registerCallback(callback)
        resolveCaller(context, numberOf(call))
        if (call.details.state == Call.STATE_ACTIVE) wasActive.add(call)
        if (calls.size == 1) dtmfHistory = ""
        changed()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        wasActive.remove(call)
        changed()
    }

    internal fun audioChanged(state: CallAudioState?) {
        _audio.value = state
        changed()
    }

    private fun changed() {
        _tick.value = _tick.value + 1
        appContext?.let { CallNotifications.update(it) }
    }

    private fun resolveCaller(context: Context, number: String) {
        if (number.isBlank() || callers.containsKey(number)) return
        scope.launch(Dispatchers.IO) {
            val id = runCatching { ContactsRepo(context.applicationContext).lookup(number) }.getOrNull()
            if (id != null) {
                callers[number] = id
                launch(Dispatchers.Main) { changed() }
            }
        }
    }

    fun numberOf(call: Call): String = call.details?.handle?.schemeSpecificPart ?: ""

    private fun view(call: Call): CallView {
        val d = call.details
        val number = numberOf(call)
        val who = callers[number]
        val state = call.details.state
        val hidden = number.isBlank() || (Build.VERSION.SDK_INT >= 30 && d.handlePresentation != android.telecom.TelecomManager.PRESENTATION_ALLOWED)
        // Nazwa z sieci (CNAP) albo z konferencji, gdy numer nie jest w kontaktach.
        val netName = d.callerDisplayName?.takeIf { it.isNotBlank() } ?: d.contactDisplayName?.takeIf { it.isNotBlank() }
        val account = sims.takeIf { it.size > 1 }?.firstOrNull { it.id == d.accountHandle?.id }?.label
        return CallView(
            call = call,
            number = number,
            name = who?.name ?: netName,
            photoUri = who?.photoUri,
            label = who?.label,
            contactId = who?.contactId,
            state = state,
            connectTime = d.connectTimeMillis,
            conference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            children = call.children.size,
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMerge = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            canSwap = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            canMute = d.can(Call.Details.CAPABILITY_MUTE),
            video = VideoProfile.isVideo(d.videoState),
            hidden = hidden,
            accountLabel = account,
            disconnect = d.disconnectCause,
        )
    }

    /** Połączenia najwyższego poziomu (uczestnicy konferencji są w niej ukryci). */
    fun snapshot(): List<CallView> = calls.filter { it.parent == null && it.details.state != Call.STATE_DISCONNECTED }.map { view(it) }

    fun conferenceMembers(conf: Call): List<CallView> = conf.children.map { view(it) }

    /** Połączenie na pierwszym planie: dzwoniące, potem wybierane/aktywne, na końcu zawieszone. */
    fun primary(list: List<CallView> = snapshot()): CallView? =
        list.firstOrNull { it.ringing } ?: list.firstOrNull { it.dialing } ?: list.firstOrNull { it.active } ?: list.firstOrNull { it.held }

    fun hasCalls(): Boolean = snapshot().isNotEmpty()

    fun answer(call: Call, video: Boolean = false) = call.answer(if (video) VideoProfile.STATE_BIDIRECTIONAL else VideoProfile.STATE_AUDIO_ONLY)

    /** Odrzucenie; z [message] – z wiadomością SMS (przez domyślną aplikację SMS). */
    fun reject(call: Call, message: String? = null) {
        if (message != null) call.reject(true, message) else call.reject(false, null)
    }

    /** Wycisza dzwonek bez odrzucania – rozmówca dalej słyszy sygnał (działa jako domyślny telefon). */
    @android.annotation.SuppressLint("MissingPermission")
    fun silenceRinger() {
        val ctx = appContext ?: return
        runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.silenceRinger() }
    }

    fun hangup(call: Call) {
        if (call.details.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    /** Odbiera nowe połączenie, kończąc bieżące („Zakończ i odbierz”). */
    fun endAndAnswer(ringing: Call) {
        calls.filter { it != ringing && it.parent == null }.forEach { it.disconnect() }
        answer(ringing)
    }

    /** Odbiera nowe połączenie, zawieszając bieżące („Zawieś i odbierz”). */
    fun holdAndAnswer(ringing: Call) {
        calls.filter { it != ringing && it.parent == null && it.details.state == Call.STATE_ACTIVE }.forEach { it.hold() }
        answer(ringing)
    }

    fun toggleHold(call: Call) {
        if (call.details.state == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun swap() {
        val list = snapshot()
        val conf = list.firstOrNull { it.conference && it.canSwap }
        if (conf != null) {
            conf.call.swapConference()
            return
        }
        val active = list.firstOrNull { it.active }
        val held = list.firstOrNull { it.held }
        if (held != null) {
            // unhold automatycznie zawiesza aktywne połączenie.
            held.call.unhold()
        } else {
            active?.call?.hold()
        }
    }

    fun merge() {
        val list = snapshot()
        val active = list.firstOrNull { it.active } ?: list.firstOrNull() ?: return
        val other = active.call.conferenceableCalls.firstOrNull()
        if (other != null) active.call.conference(other) else active.call.mergeConference()
    }

    fun mute(on: Boolean) {
        service?.setMuted(on)
    }

    // setAudioRoute działa na wszystkich wersjach; requestCallEndpointChange (API 34) nie daje nic więcej.
    @Suppress("DEPRECATION")
    fun route(route: Int) {
        service?.setAudioRoute(route)
    }

    @Suppress("DEPRECATION")
    fun bluetooth(device: android.bluetooth.BluetoothDevice) {
        service?.requestBluetoothAudio(device)
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val a = _audio.value ?: return
        route(if (a.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    fun dtmf(call: Call, c: Char) {
        call.playDtmfTone(c)
        dtmfHistory += c
        scope.launch {
            delay(160)
            call.stopDtmfTone()
        }
    }

    private fun onConnected() {
        val ctx = appContext ?: return
        if (SettingsStore(ctx).load().vibrateOnConnect) vibrate(ctx, 60)
    }

    private fun onDisconnected(call: Call) {
        val ctx = appContext ?: return
        lastEnded = view(call)
        if (wasActive.contains(call) && SettingsStore(ctx).load().vibrateOnEnd) vibrate(ctx, 120)
    }

    private fun vibrate(context: Context, ms: Long) {
        runCatching {
            val v = context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
