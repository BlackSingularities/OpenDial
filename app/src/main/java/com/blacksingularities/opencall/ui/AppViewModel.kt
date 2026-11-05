package com.blacksingularities.opencall.ui

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacksingularities.opencall.ai.AgentLine
import com.blacksingularities.opencall.ai.AgentPlatform
import com.blacksingularities.opencall.ai.AiLog
import com.blacksingularities.opencall.ai.AiPrompt
import com.blacksingularities.opencall.ai.AiProvider
import com.blacksingularities.opencall.ai.AiRecord
import com.blacksingularities.opencall.ai.AiRules
import com.blacksingularities.opencall.ai.AiSync
import com.blacksingularities.opencall.ai.SecretStore
import com.blacksingularities.opencall.call.Dialer
import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.BlockRepo
import com.blacksingularities.opencall.data.CallEntry
import com.blacksingularities.opencall.data.CallGroup
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.ContactDraft
import com.blacksingularities.opencall.data.ContactsRepo
import com.blacksingularities.opencall.data.DraftField
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.data.SimAccount
import com.blacksingularities.opencall.data.StartTab
import com.blacksingularities.opencall.data.VCard
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.update.UpdateManager
import com.blacksingularities.opencall.update.UpdateState
import com.blacksingularities.opencall.update.isNewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ekrany nad zakładkami głównymi (stos – „wstecz” zdejmuje ostatni). */
sealed interface Route {
    data class ContactInfo(val id: Long) : Route
    data class NumberInfo(val number: String) : Route
    data class Edit(val draft: ContactDraft) : Route
    data object Settings : Route
    data object Blocked : Route
    data object AiScreening : Route
    data object AiHistory : Route
    data object Stats : Route
    data object Duplicates : Route
    data object SpeedDial : Route
    data object QuickReplies : Route
    data object Backup : Route
    /** Ulubione jako osobny ekran, gdy zakładka na dolnym pasku jest wyłączona. */
    data object Favorites : Route
}

/** Połączenie czekające na wybór karty SIM albo potwierdzenie. */
data class PendingCall(val number: String, val name: String?, val needsSim: Boolean, val needsConfirm: Boolean)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val store = SettingsStore(app)
    val contactsRepo = ContactsRepo(app)
    val callLogRepo = CallLogRepo(app)
    val blockRepo = BlockRepo(app)

    var settings by mutableStateOf(store.load())
        private set

    var tab by mutableIntStateOf(
        when (settings.startTab) {
            StartTab.FAVORITES -> 0
            StartTab.RECENTS -> 1
            StartTab.CONTACTS -> 2
            StartTab.KEYPAD -> 1
            // Dawna osobna zakładka „Klawiatura” (3) jest teraz częścią „Telefonu”.
            StartTab.LAST -> settings.lastTab.let { if (it == 3) 1 else it.coerceIn(0, 2) }
        }.let { if (it == 0 && !settings.showFavoritesTab) 1 else it },
    )
        private set

    /** Klawiatura rozwinięta nad historią w zakładce „Telefon”. */
    var keypadOpen by mutableStateOf(settings.startTab == StartTab.KEYPAD || (settings.startTab == StartTab.LAST && settings.lastTab == 3))

    val stack = mutableStateListOf<Route>()
    val route: Route? get() = stack.lastOrNull()

    var contacts by mutableStateOf<List<Contact>>(emptyList())
        private set
    var calls by mutableStateOf<List<CallEntry>>(emptyList())
        private set
    var groups by mutableStateOf<List<CallGroup>>(emptyList())
        private set
    var unseenMissed by mutableIntStateOf(0)
        private set
    var blocked by mutableStateOf<List<String>>(emptyList())
        private set
    var sims by mutableStateOf<List<SimAccount>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    var isDefaultDialer by mutableStateOf(false)
        private set
    var hasContacts by mutableStateOf(false)
        private set
    var hasCallLog by mutableStateOf(false)
        private set
    var hasPhone by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set
    private var messageJob: Job? = null

    var pendingCall by mutableStateOf<PendingCall?>(null)

    /** Trwa zapis na kartę SIM (z odczytem kontrolnym) – przycisk „Zapisz” jest wtedy wyłączony. */
    var simSaving by mutableStateOf(false)
        private set

    /** Cyfry na klawiaturze – przetrwają zmianę zakładki. */
    var dialed by mutableStateOf("")

    /** Tryb „dodaj połączenie” w trakcie rozmowy (otwarty z ekranu rozmowy). */
    var addingCall by mutableStateOf(false)

    var update by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var updateBannerHidden by mutableStateOf(false)
    private var lastUpdateCheck = 0L

    private var reloadJob: Job? = null
    // Osobne obserwatory: zmiana w rejestrze połączeń nie wczytuje od nowa tysięcy kontaktów i odwrotnie.
    private var dirtyContacts = false
    private var dirtyLog = false
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            dirtyContacts = true
            scheduleReload()
        }
    }
    private val logObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            dirtyLog = true
            scheduleReload()
        }
    }

    /** Ostatnio wczytany rejestr bez nazw z kontaktów – do ponownego podpięcia po zmianie kontaktów. */
    private var rawCalls: List<CallEntry> = emptyList()

    /** Numer (klucz [PhoneText.key]) → kontakt; zamiast przeszukiwania całej listy przy każdym wierszu. */
    private var contactIndex: Map<String, Contact> = emptyMap()

    init {
        I18n.apply(settings.language)
        runCatching {
            ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver)
            ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, logObserver)
        }
    }

    override fun onCleared() {
        VoicemailPlayer.stop()
        runCatching { ctx.contentResolver.unregisterContentObserver(contactsObserver) }
        runCatching { ctx.contentResolver.unregisterContentObserver(logObserver) }
        super.onCleared()
    }

    fun updateSettings(t: (AppSettings) -> AppSettings) {
        val old = settings
        settings = t(settings)
        store.save(settings)
        if (old.language != settings.language) I18n.apply(settings.language)
        if (old.nameOrder != settings.nameOrder || old.groupCalls != settings.groupCalls) reload()
        // Zakładka właśnie zniknęła z paska – nie zostawiamy jej otwartej.
        if (!settings.showFavoritesTab && tab == 0) selectTab(1)
    }

    // ---- Asystent AI --------------------------------------------------------------------------

    private val secrets by lazy { SecretStore(ctx) }
    private val aiLog by lazy { AiLog(ctx) }

    var aiKey by mutableStateOf("")
        private set
    var aiLines by mutableStateOf<List<AgentLine>>(emptyList())
        private set
    var aiBusy by mutableStateOf(false)
        private set
    var aiRecords by mutableStateOf<List<AiRecord>>(emptyList())
        private set

    fun updateAi(t: (com.blacksingularities.opencall.ai.AiScreening) -> com.blacksingularities.opencall.ai.AiScreening) =
        updateSettings { it.copy(ai = t(it.ai)) }

    /** Klucz i historia ładowane dopiero po wejściu na ekran asystenta. */
    fun loadAi() {
        viewModelScope.launch {
            aiKey = withContext(Dispatchers.IO) { secrets.get(settings.ai.provider.name) }
            aiRecords = withContext(Dispatchers.IO) { aiLog.load() }
        }
    }

    fun saveAiKey(value: String) {
        aiKey = value.trim()
        val name = settings.ai.provider.name
        val key = aiKey
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { secrets.put(name, key) }
            if (!saved) toast(tr("Nie udało się bezpiecznie zapisać klucza"))
        }
    }

    fun setAiProvider(p: AiProvider) {
        if (p == settings.ai.provider) return
        aiLines = emptyList()
        updateAi { it.copy(provider = p, agentId = "", lineId = "", agentNumber = "", syncedHash = "", model = "", voice = "") }
        loadAi()
    }

    private fun aiPlatform() = AgentPlatform.of(settings.ai.provider, aiKey, settings.ai.customUrl)

    /** Test klucza i pobranie numerów z konta. */
    fun aiConnect() {
        if (aiBusy) return
        aiBusy = true
        viewModelScope.launch {
            val platform = aiPlatform()
            val result = withContext(Dispatchers.IO) { runCatching { platform.lines() } }
            aiBusy = false
            result.onSuccess { lines ->
                aiLines = lines
                aiLog.lastSync = System.currentTimeMillis()
                when {
                    settings.ai.provider == AiProvider.CUSTOM -> toast(tr("Serwer odpowiada"))
                    lines.isEmpty() -> toast(tr("Klucz działa, ale na koncie nie ma numeru – dodaj go w panelu platformy"))
                    lines.size == 1 -> aiPickLine(lines[0])
                    else -> toast(tr("Klucz działa – wybierz numer"))
                }
            }.onFailure { toast(it.message ?: tr("Brak połączenia")) }
        }
    }

    fun aiPickLine(line: AgentLine) {
        updateAi { it.copy(lineId = line.id, agentNumber = line.number, agentId = line.agentId.ifBlank { it.agentId }) }
    }

    /** Wysyła powitanie, instrukcję i narzędzia do agenta na platformie. */
    fun aiSendConfig() {
        if (aiBusy) return
        val cfg = settings.ai
        if (cfg.ownNumber.isBlank()) {
            toast(tr("Podaj swój numer – na niego agent przełączy ważne rozmowy"))
            return
        }
        aiBusy = true
        viewModelScope.launch {
            val platform = aiPlatform()
            val result = withContext(Dispatchers.IO) { runCatching { platform.sync(cfg) } }
            aiBusy = false
            result.onSuccess { id ->
                val saved = cfg.copy(agentId = id)
                updateAi { it.copy(agentId = id, syncedHash = AiPrompt.fingerprint(saved)) }
                toast(tr("Agent zaktualizowany"))
            }.onFailure { toast(it.message ?: tr("Brak połączenia")) }
        }
    }

    /** Kod MMI idzie do operatora jak połączenie, a odpowiedź pokazuje system. */
    fun aiForwarding(on: Boolean) {
        val cfg = settings.ai
        val code = if (on) AiRules.enableCode(cfg) else AiRules.disableCode(cfg)
        if (Dialer.call(ctx, code, settings.defaultSim)) {
            updateAi { it.copy(forwardingOn = on) }
        } else {
            toast(tr("Brak uprawnienia do połączeń"))
        }
    }

    fun aiRefresh() {
        if (aiBusy) return
        aiBusy = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { AiSync.run(ctx) } }
            aiRecords = withContext(Dispatchers.IO) { aiLog.load() }
            aiBusy = false
        }
    }

    fun aiDelete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { aiLog.update { list -> list.filterNot { it.id == id } } }
            aiRecords = withContext(Dispatchers.IO) { aiLog.load() }
        }
    }

    fun selectTab(i: Int) {
        tab = i
        if (settings.lastTab != i) updateSettings { it.copy(lastTab = i) }
        if (i == 1) markMissedSeen()
    }

    /** Zakładka „Telefon” z rozwiniętą klawiaturą. */
    fun openKeypad() {
        selectTab(1)
        keypadOpen = true
    }

    fun push(r: Route) {
        stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(r: Route) {
        if (stack.isNotEmpty()) stack[stack.lastIndex] = r else stack.add(r)
    }

    fun toast(text: String) {
        message = text
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2200)
            message = null
        }
    }

    // ---- Uprawnienia i dane ------------------------------------------------------------------

    fun refreshPermissions() {
        isDefaultDialer = Dialer.isDefault(ctx)
        hasContacts = Dialer.has(ctx, Manifest.permission.READ_CONTACTS)
        hasCallLog = Dialer.has(ctx, Manifest.permission.READ_CALL_LOG)
        hasPhone = Dialer.has(ctx, Manifest.permission.CALL_PHONE)
    }

    val missingPermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { !Dialer.has(ctx, it) }.toTypedArray()

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(400)
            val c = dirtyContacts
            val l = dirtyLog
            dirtyContacts = false
            dirtyLog = false
            reload(contactsToo = c || !loaded, logToo = l || !loaded)
        }
    }

    /** Po powrocie do aplikacji: pełne wczytanie tylko przy zmianie uprawnień lub roli (resztę łapią obserwatory). */
    fun refreshOnResume() {
        val before = listOf(isDefaultDialer, hasContacts, hasCallLog, hasPhone)
        refreshPermissions()
        if (!loaded || before != listOf(isDefaultDialer, hasContacts, hasCallLog, hasPhone)) reload()
    }

    fun reload(contactsToo: Boolean = true, logToo: Boolean = true) {
        refreshPermissions()
        viewModelScope.launch {
            val s = settings
            val c = when {
                !hasContacts -> emptyList()
                contactsToo || !loaded -> withContext(Dispatchers.IO) { runCatching { contactsRepo.loadAll(s.nameOrder) }.getOrDefault(emptyList()) }
                else -> contacts
            }
            val l = when {
                !hasCallLog -> emptyList()
                logToo || !loaded -> withContext(Dispatchers.IO) { runCatching { callLogRepo.load() }.getOrDefault(emptyList()) }
                else -> rawCalls
            }
            rawCalls = l
            if (c !== contacts) contactIndex = withContext(Dispatchers.Default) { indexContacts(c) }
            // Nazwy z bieżących kontaktów – CACHED_NAME w rejestrze bywa nieaktualny.
            val index = contactIndex
            val named = withContext(Dispatchers.Default) { attachNames(l, index) }
            contacts = c
            calls = named
            // Skróty z ikony i widżet ulubionych – tylko gdy zmieniły się kontakty.
            if (contactsToo) withContext(Dispatchers.IO) { runCatching { com.blacksingularities.opencall.launcher.Launcher.update(ctx, c) } }
            groups = withContext(Dispatchers.Default) { PhoneText.group(named, s.groupCalls) }
            unseenMissed = if (hasCallLog) withContext(Dispatchers.IO) { callLogRepo.unseenMissed() } else 0
            blocked = withContext(Dispatchers.IO) { blockRepo.list() }
            sims = withContext(Dispatchers.IO) { Dialer.sims(ctx) }
            loaded = true
            if (tab == 1) markMissedSeen()
        }
    }

    private fun indexContacts(contacts: List<Contact>): Map<String, Contact> {
        val byKey = HashMap<String, Contact>(contacts.size * 2)
        contacts.forEach { c -> c.numbers.forEach { n -> PhoneText.key(n.number).takeIf { it.isNotEmpty() }?.let { byKey.putIfAbsent(it, c) } } }
        return byKey
    }

    private fun attachNames(log: List<CallEntry>, byKey: Map<String, Contact>): List<CallEntry> {
        if (byKey.isEmpty()) return log
        return log.map { e ->
            val c = if (e.hidden) null else byKey[PhoneText.key(e.number)]
            if (c != null) e.copy(name = c.name, photoUri = c.photoUri ?: e.photoUri) else e
        }
    }

    fun contactFor(number: String): Contact? {
        val k = PhoneText.key(number)
        if (k.isEmpty()) return null
        return contactIndex[k]
    }

    fun markMissedSeen() {
        if (unseenMissed == 0 || !hasCallLog) return
        unseenMissed = 0
        viewModelScope.launch(Dispatchers.IO) {
            callLogRepo.markMissedSeen()
            Dialer.cancelMissedCallsNotification(ctx)
            com.blacksingularities.opencall.call.CallNotifications.cancelMissed(ctx)
        }
    }

    // ---- Dzwonienie --------------------------------------------------------------------------

    /** Dzwoni z uwzględnieniem potwierdzenia i wyboru karty SIM. */
    fun call(number: String, name: String? = null, simId: String? = null) {
        val n = number.trim()
        if (n.isEmpty()) return
        if (Dialer.handleSecretCode(ctx, n) || deviceCode(n)) {
            dialed = ""
            return
        }
        if (Dialer.isEmergency(ctx, n)) {
            // Bez pytań i bez wyboru karty: liczy się każda sekunda.
            pendingCall = null
            place(n, null)
            return
        }
        val needsSim = simId == null && sims.size > 1 && settings.defaultSim == null
        val needsConfirm = settings.confirmCall && pendingCall == null
        if (needsSim || needsConfirm) {
            pendingCall = PendingCall(n, name, needsSim, needsConfirm)
            return
        }
        pendingCall = null
        place(n, simId ?: settings.defaultSim)
    }

    /** Okno informacyjne (IMEI itp.): tytuł i treść. */
    var infoDialog by mutableStateOf<Pair<String, String>?>(null)

    /** Kody obsługiwane przez sam dialer: „*#06#” – IMEI, „*#07#” – SAR. */
    fun deviceCode(number: String): Boolean = when (number.trim()) {
        "*#06#" -> {
            val ids = Dialer.deviceIds(ctx)
            if (ids.isEmpty()) {
                // Android 10+ nie pokazuje IMEI zwykłym aplikacjom – jest w „Informacjach o telefonie”.
                toast(tr("IMEI znajdziesz w Informacjach o telefonie"))
                openIntent(Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS))
            } else {
                infoDialog = tr("IMEI") to ids.mapIndexed { i, id -> if (ids.size > 1) "SIM ${i + 1}: $id" else id }.joinToString("
")
            }
            true
        }
        "*#07#" -> {
            toast(tr("Wartości SAR są w Informacjach o telefonie → Informacje prawne"))
            openIntent(Intent(android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS))
            true
        }
        else -> false
    }

    fun instantCode() {
        val n = dialed
        if (Dialer.handleSecretCode(ctx, n) || deviceCode(n)) dialed = ""
    }

    fun confirmPending(simId: String?) {
        val p = pendingCall ?: return
        pendingCall = null
        place(p.number, simId ?: settings.defaultSim)
    }

    private fun place(number: String, simId: String?) {
        if (!Dialer.call(ctx, number, simId)) {
            // Bez uprawnienia CALL_PHONE: systemowy ekran wybierania z numerem.
            openIntent(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
        } else {
            addingCall = false
        }
    }

    fun voicemail() {
        if (!Dialer.voicemail(ctx, settings.defaultSim)) toast(tr("Brak numeru poczty głosowej"))
    }

    fun speedDial(digit: Int): Boolean {
        val n = settings.speedDial[digit] ?: return false
        call(n)
        return true
    }

    // ---- Akcje -------------------------------------------------------------------------------

    fun openIntent(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast(tr("Brak aplikacji do wykonania tej czynności")) }
    }

    fun sms(number: String) = openIntent(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))

    fun email(address: String) = openIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address))))

    fun map(address: String) = openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))

    fun web(url: String) = openIntent(Intent(Intent.ACTION_VIEW, Uri.parse(if (url.contains("://")) url else "https://$url")))

    fun copy(text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("number", text))
        if (Build.VERSION.SDK_INT < 33) toast(tr("Skopiowano"))
    }

    fun pasteNumber(): String? {
        val clip = ctx.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        val text = clip.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: return null
        val cleaned = text.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        return cleaned.takeIf { it.isNotEmpty() }
    }

    fun shareText(text: String, subject: String? = null) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject)
        openIntent(Intent.createChooser(send, tr("Udostępnij")))
    }

    fun shareVCard(id: Long) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { contactsRepo.draft(id) } ?: return@launch
            val text = VCard.write(d)
            val file = withContext(Dispatchers.IO) {
                val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs() }
                java.io.File(dir, (d.displayName.ifBlank { "kontakt" }.replace(Regex("[^\\p{L}\\p{N} _-]"), "")) + ".vcf").apply { writeText(text) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openIntent(Intent.createChooser(send, tr("Udostępnij kontakt")))
        }
    }

    fun setStarred(id: Long, starred: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { contactsRepo.setStarred(id, starred) } }
        contacts = contacts.map { if (it.id == id) it.copy(starred = starred) else it }
        toast(if (starred) tr("Dodano do ulubionych") else tr("Usunięto z ulubionych"))
    }

    fun deleteContacts(ids: Collection<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { contactsRepo.delete(ids) } }
            contacts = contacts.filter { it.id !in ids }
            toast(if (ids.size == 1) tr("Usunięto kontakt") else tr("Usunięto kontakty: {0}", ids.size))
        }
    }

    fun saveContact(d: ContactDraft, onSaved: (Long?) -> Unit) {
        viewModelScope.launch {
            if (d.account?.sim == true) {
                // Karta SIM: zapis i odczyt kontrolny trwają chwilę; formularz zostaje otwarty przy błędzie.
                simSaving = true
                val error = withContext(Dispatchers.IO) { runCatching { contactsRepo.saveSim(d) }.getOrElse { tr("Karta SIM odrzuciła zapis") } }
                simSaving = false
                if (error != null) {
                    toast(error)
                    return@launch
                }
                toast(tr("Zapisano na karcie SIM"))
                reload()
                onSaved(d.id)
                return@launch
            }
            val id = withContext(Dispatchers.IO) { runCatching { contactsRepo.save(d) }.getOrNull() }
            if (id == null && d.id == null) toast(tr("Nie udało się zapisać kontaktu")) else toast(tr("Zapisano"))
            reload()
            onSaved(id)
        }
    }

    fun newContact(number: String? = null, name: String? = null) {
        val parts = name?.trim()?.split(Regex("\\s+")).orEmpty()
        val accounts = runCatching { contactsRepo.accounts() }.getOrDefault(emptyList())
        push(
            Route.Edit(
                ContactDraft(
                    given = parts.firstOrNull().orEmpty(),
                    family = parts.drop(1).joinToString(" "),
                    phones = listOf(DraftField(number.orEmpty(), ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)),
                    // Domyślnie konto synchronizowane, nigdy karta SIM (ta mieści tylko nazwę i numer).
                    account = accounts.firstOrNull { !it.local && !it.sim } ?: accounts.firstOrNull(),
                ),
            ),
        )
    }

    fun editContact(id: Long) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { contactsRepo.draft(id) }
            if (d != null) push(Route.Edit(d)) else toast(tr("Nie można edytować tego kontaktu"))
        }
    }

    /** Numer dopisany do istniejącego kontaktu (np. z rejestru połączeń). */
    fun addNumberTo(contactId: Long, number: String) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { contactsRepo.draft(contactId) } ?: return@launch
            val phones = d.phones.filter { it.value.isNotBlank() } + DraftField(number, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            push(Route.Edit(d.copy(phones = phones)))
        }
    }

    fun block(number: String) {
        if (!blockRepo.canAccess() || !isDefaultDialer) {
            toast(tr("Blokowanie wymaga roli domyślnej aplikacji telefonu"))
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { blockRepo.block(number) }
            blocked = withContext(Dispatchers.IO) { blockRepo.list() }
            toast(if (ok) tr("Zablokowano {0}", PhoneText.pretty(number)) else tr("Nie udało się zablokować numeru"))
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { blockRepo.unblock(number) }
            blocked = withContext(Dispatchers.IO) { blockRepo.list() }
            toast(tr("Odblokowano {0}", PhoneText.pretty(number)))
        }
    }

    fun isBlocked(number: String): Boolean = blocked.any { PhoneText.sameNumber(it, number) }

    fun deleteCalls(ids: Collection<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { callLogRepo.delete(ids) } }
            reload()
            toast(tr("Usunięto z historii"))
        }
    }

    fun clearCallLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { callLogRepo.clear() } }
            reload()
            toast(tr("Historia połączeń wyczyszczona"))
        }
    }

    fun mergeContacts(ids: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { contactsRepo.merge(ids) } }
            reload()
            toast(tr("Kontakty scalone"))
        }
    }

    // ---- Kopia zapasowa ----------------------------------------------------------------------

    fun exportContacts(uri: Uri) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    val drafts = contacts.mapNotNull { contactsRepo.draft(it.id) }
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(VCard.writeAll(drafts).toByteArray()) }
                    drafts.size
                }.getOrNull()
            }
            toast(if (n != null) tr("Wyeksportowano kontakty: {0}", n) else tr("Eksport nieudany"))
        }
    }

    fun importContacts(uri: Uri) {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    val accounts = contactsRepo.accounts()
                    val account = accounts.firstOrNull { !it.local && !it.sim } ?: accounts.firstOrNull()
                    val list = VCard.parse(text)
                    // Pomijamy kontakty, które już są (ta sama nazwa i numer).
                    val existing = contacts.flatMap { c -> c.numbers.map { PhoneText.key(it.number) + "|" + c.searchName } }.toHashSet()
                    var added = 0
                    list.forEach { d ->
                        val keys = d.phones.map { PhoneText.key(it.value) + "|" + PhoneText.fold(d.displayName) }
                        if (keys.isNotEmpty() && keys.all { it in existing }) return@forEach
                        if (contactsRepo.save(d.copy(account = account)) != null) added++
                    }
                    added
                }.getOrNull()
            }
            reload()
            toast(if (n != null) tr("Zaimportowano kontakty: {0}", n) else tr("Import nieudany"))
        }
    }

    fun exportCallLog(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = CallLogRepo.toCsv(calls, { Format.kind(it) }, { Format.csvDate(it) })
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray()) }
                }.isSuccess
            }
            toast(if (ok) tr("Wyeksportowano historię: {0}", calls.size) else tr("Eksport nieudany"))
        }
    }

    // ---- Aktualizacje ------------------------------------------------------------------------

    fun checkUpdateIfDue() {
        if (!settings.autoUpdate) return
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck < 6 * 60 * 60_000L) return
        checkUpdate(manual = false)
    }

    fun checkUpdate(manual: Boolean = true) {
        if (update is UpdateState.Checking || update is UpdateState.Downloading || update is UpdateState.Installing) return
        lastUpdateCheck = System.currentTimeMillis()
        update = UpdateState.Checking
        viewModelScope.launch {
            update = runCatching {
                val info = UpdateManager.check()
                if (isNewer(info.version, UpdateManager.currentVersion)) UpdateState.Available(info) else UpdateState.UpToDate
            }.getOrElse { e -> if (manual) UpdateState.Failed(e.message ?: tr("Brak połączenia")) else UpdateState.Idle }
            if (update is UpdateState.Available) updateBannerHidden = false
        }
    }

    fun updateNow() {
        when (val u = update) {
            is UpdateState.Available -> {
                if (!UpdateManager.canInstall(ctx)) {
                    UpdateManager.openInstallPermissionSettings(ctx)
                    toast(tr("Zezwól na instalację aktualizacji i dotknij ponownie"))
                    return
                }
                viewModelScope.launch {
                    update = UpdateState.Downloading(u.info, 0f)
                    update = runCatching {
                        val f = UpdateManager.download(ctx, u.info) { p -> update = UpdateState.Downloading(u.info, p) }
                        UpdateState.Ready(u.info, f)
                    }.getOrElse { UpdateState.Failed(it.message ?: tr("Pobieranie nie powiodło się")) }
                    (update as? UpdateState.Ready)?.let { install(it) }
                }
            }
            is UpdateState.Ready -> install(u)
            else -> checkUpdate()
        }
    }

    private fun install(r: UpdateState.Ready) {
        update = UpdateState.Installing(r.info)
        viewModelScope.launch {
            runCatching { UpdateManager.install(ctx, r.file) }
                .onFailure { update = UpdateState.Failed(it.message ?: tr("Instalacja nieudana")) }
            delay(8000)
            if (update is UpdateState.Installing) update = r
        }
    }

    val updatePending: Boolean
        get() = update is UpdateState.Available || update is UpdateState.Downloading || update is UpdateState.Ready || update is UpdateState.Installing
}
