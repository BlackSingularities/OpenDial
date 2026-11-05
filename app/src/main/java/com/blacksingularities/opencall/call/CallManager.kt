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
import com.blacksingularities.opencall.ai.AiLog
import com.blacksingularities.opencall.ai.AiMatch
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.tr
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
    /** Trwa rozmowa tekstowa w czasie rzeczywistym (RTT). */
    val rtt: Boolean = false,
    /** Sieć i karta pozwalają włączyć RTT. */
    val rttCapable: Boolean = false,
    /** Połączenie alarmowe – ekran rozmowy to zaznacza. */
    val emergency: Boolean = false,
    /** Połączenie przez Wi-Fi (Wi-Fi Calling). */
    val wifi: Boolean = false,
    /** Dźwięk HD (zwykle VoLTE lub VoWiFi). */
    val hd: Boolean = false,
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

    /** Numer agenta AI i to, kogo właśnie przełącza – połączenie od niego nie jest „nieznanym numerem”. */
    private var agentNumber = ""
    private var agentCaller: String? = null

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
            if (state == Call.STATE_ACTIVE && wasActive.add(call)) onConnected(call)
            if (state == Call.STATE_DISCONNECTED) onDisconnected(call)
            changed()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
        override fun onChildrenChanged(call: Call, children: MutableList<Call>) = changed()
        override fun onParentChanged(call: Call, parent: Call?) = changed()
        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = changed()
        override fun onCallDestroyed(call: Call) = changed()

        override fun onRttStatusChanged(call: Call, enabled: Boolean, rttCall: Call.RttCall?) {
            if (enabled && rttCall != null) startRttReader(call, rttCall)
            changed()
        }
        override fun onRttRequest(call: Call, id: Int) {
            rttRequest = call to id
            changed()
        }
        override fun onRttInitiationFailure(call: Call, reason: Int) {
            appContext?.let { android.widget.Toast.makeText(it, tr("Nie udało się włączyć RTT"), android.widget.Toast.LENGTH_SHORT).show() }
            changed()
        }
        override fun onRttModeChanged(call: Call, mode: Int) = changed()
    }

    // ---- RTT (tekst w czasie rzeczywistym) ---------------------------------------------------

    /** Wiersz rozmowy tekstowej: [mine] – wpisany przeze mnie. */
    data class RttLine(val mine: Boolean, val text: String)

    private val rttLines = ConcurrentHashMap<Call, MutableList<RttLine>>()
    private val rttReaders = ConcurrentHashMap<Call, Thread>()

    /** Prośba rozmówcy o RTT czekająca na odpowiedź (połączenie i identyfikator prośby). */
    var rttRequest: Pair<Call, Int>? = null
        private set

    fun rttTranscript(call: Call): List<RttLine> = rttLines[call]?.let { synchronized(it) { it.toList() } }.orEmpty()

    fun requestRtt(call: Call) = runCatching { call.sendRttRequest() }

    fun stopRtt(call: Call) = runCatching { call.stopRtt() }

    fun answerRttRequest(accept: Boolean) {
        val (call, id) = rttRequest ?: return
        rttRequest = null
        runCatching { call.respondToRttRequest(id, accept) }
        changed()
    }

    /** Wysyła znaki od razu, jak w RTT: dopisane litery albo „\b” za każdy skasowany znak. */
    fun sendRtt(call: Call, text: String) {
        if (text.isEmpty()) return
        val rtt = call.rttCall ?: return
        scope.launch(Dispatchers.IO) { runCatching { rtt.write(text) } }
    }

    /** Zapisuje moją wypowiedź w historii (wysyłaną po kawałku przez [sendRtt]). */
    fun rttCommitMine(call: Call, line: String) {
        if (line.isBlank()) return
        appendRtt(call, mine = true, text = line, newLine = true)
    }

    private fun appendRtt(call: Call, mine: Boolean, text: String, newLine: Boolean) {
        val list = rttLines.getOrPut(call) { java.util.Collections.synchronizedList(ArrayList()) }
        synchronized(list) {
            var rest = text
            while (rest.isNotEmpty()) {
                val last = list.lastOrNull()
                val cut = rest.indexOf('\n')
                val chunk = if (cut >= 0) rest.substring(0, cut) else rest
                if (!newLine && last != null && last.mine == mine && !last.text.endsWith("\n")) {
                    // Backspace z drugiej strony kasuje ostatni znak.
                    var t = last.text
                    chunk.forEach { ch -> t = if (ch == '\b') t.dropLast(1) else t + ch }
                    list[list.lastIndex] = last.copy(text = t)
                } else {
                    list.add(RttLine(mine, chunk.filter { it != '\b' }))
                }
                if (cut >= 0) {
                    list[list.lastIndex] = list.last().let { it.copy(text = it.text + "\n") }
                    rest = rest.substring(cut + 1)
                } else {
                    rest = ""
                }
            }
        }
        scope.launch { changed() }
    }

    private fun startRttReader(call: Call, rtt: Call.RttCall) {
        if (rttReaders[call]?.isAlive == true) return
        val t = Thread {
            while (true) {
                val text = runCatching { rtt.read() }.getOrNull() ?: break
                appendRtt(call, mine = false, text = text, newLine = false)
            }
            rttReaders.remove(call)
        }.apply { isDaemon = true; name = "rtt-reader" }
        rttReaders[call] = t
        t.start()
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    internal fun add(context: Context, call: Call) {
        attach(context)
        if (calls.contains(call)) return
        if (calls.isEmpty()) sims = Dialer.sims(context)
        readAgent(context, numberOf(call))
        calls.add(call)
        call.registerCallback(callback)
        call.rttCall?.let { startRttReader(call, it) }
        resolveCaller(context, numberOf(call))
        if (call.details.state == Call.STATE_ACTIVE) wasActive.add(call)
        if (calls.size == 1) dtmfHistory = ""
        changed()
    }

    internal fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        wasActive.remove(call)
        rttLines.remove(call)
        rttReaders.remove(call)?.interrupt()
        if (rttRequest?.first == call) rttRequest = null
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

    private fun readAgent(context: Context, number: String) {
        val ai = SettingsStore(context).load().ai
        agentNumber = if (ai.enabled) ai.agentNumber else ""
        if (!isAgent(number)) return
        val live = AiLog(context).load().firstOrNull { it.open }
        agentCaller = live?.caller ?: live?.number?.takeIf { it.isNotBlank() }?.let { PhoneText.pretty(it) }
    }

    private fun isAgent(number: String) = agentNumber.isNotBlank() && PhoneText.sameNumber(number, agentNumber)

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
        val viaAgent = isAgent(number)
        return CallView(
            call = call,
            number = number,
            name = if (viaAgent) tr("Asystent AI") else who?.name ?: netName,
            photoUri = who?.photoUri,
            label = if (viaAgent) agentCaller else who?.label,
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
            wifi = d.hasProperty(Call.Details.PROPERTY_WIFI),
            hd = d.hasProperty(Call.Details.PROPERTY_HIGH_DEF_AUDIO),
            rtt = call.isRttActive,
            rttCapable = d.hasProperty(Call.Details.PROPERTY_RTT) || rttSupported(d.accountHandle),
            emergency = d.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) ||
                (d.callDirection == Call.Details.DIRECTION_OUTGOING && appContext?.let { Dialer.isEmergency(it, number) } == true),
        )
    }

    private val rttAccounts = ConcurrentHashMap<String, Boolean>()

    /** Czy konto telefoniczne (karta) obsługuje RTT – wynik zapamiętywany, bo UI pyta przy każdej zmianie. */
    @SuppressLint("MissingPermission")
    private fun rttSupported(handle: android.telecom.PhoneAccountHandle?): Boolean {
        val ctx = appContext ?: return false
        handle ?: return false
        return rttAccounts.getOrPut(handle.id) {
            runCatching {
                ctx.getSystemService(android.telecom.TelecomManager::class.java)?.getPhoneAccount(handle)
                    ?.hasCapabilities(android.telecom.PhoneAccount.CAPABILITY_RTT) == true
            }.getOrDefault(false)
        }
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
        // Włączenie: zawsze głośnik telefonu (także przy podłączonych słuchawkach Bluetooth).
        // Wyłączenie: z powrotem do Bluetooth, jeśli jest, inaczej słuchawka lub przewodowe.
        val off = if ((a.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0) CallAudioState.ROUTE_BLUETOOTH else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        route(if (a.route == CallAudioState.ROUTE_SPEAKER) off else CallAudioState.ROUTE_SPEAKER)
    }

    fun dtmf(call: Call, c: Char) {
        call.playDtmfTone(c)
        dtmfHistory += c
        scope.launch {
            delay(160)
            call.stopDtmfTone()
        }
    }

    private fun onConnected(call: Call) {
        val ctx = appContext ?: return
        // Odebrane osobiście przy „cichym” przekazaniu – agent nie dostanie tego połączenia.
        val number = numberOf(call)
        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING && number.isNotBlank()) {
            scope.launch(Dispatchers.IO) { runCatching { AiLog(ctx).update { AiMatch.withoutPending(it, number) } } }
        }
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
