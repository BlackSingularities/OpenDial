package com.blacksingularities.opencall.ui.screens

import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.call.Dialer
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.ContactDetails
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.RawRef
import com.blacksingularities.opencall.data.VCard
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.Group
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.components.SwitchRow
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ile wpisów historii widać przed „Pokaż wszystkie”. */
private const val HISTORY_PREVIEW = 10

/**
 * Szczegóły kontaktu ([contactId]) albo samego numeru spoza kontaktów ([number]): nagłówek z
 * plakietkami, szybkie akcje, numery, e-maile, adresy, daty, relacje, komunikatory, SIP, strony,
 * grupy, notatka, statystyki i historia połączeń, połączone kontakty oraz opcje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactInfoScreen(vm: AppViewModel, contactId: Long?, number: String?) {
    var details by remember(contactId) { mutableStateOf<ContactDetails?>(null) }
    var raws by remember(contactId) { mutableStateOf<List<RawRef>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var lastLookup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contactId, reloadKey, vm.contacts) {
        if (contactId == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { runCatching { vm.contactsRepo.details(contactId) }.getOrNull() }
        if (loaded == null) {
            // Po scaleniu, rozłączeniu albo przeniesieniu kontakt mógł dostać nowe id – szukamy go po kluczu.
            val key = lastLookup ?: return@LaunchedEffect
            val newId = withContext(Dispatchers.IO) {
                vm.contactsRepo.idForLookupUri(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, key))
            }
            if (newId != null && newId != contactId) vm.replaceTop(Route.ContactInfo(newId))
            return@LaunchedEffect
        }
        details = loaded
        lastLookup = loaded.lookupKey.takeIf { it.isNotEmpty() }
        raws = withContext(Dispatchers.IO) { runCatching { vm.contactsRepo.rawContacts(contactId) }.getOrDefault(emptyList()) }
    }
    val d = details
    if (contactId != null && d == null) {
        Page(tr("Kontakt"), onBack = { vm.pop() }) {}
        return
    }
    val numbers = d?.phones?.map { it.number } ?: listOfNotNull(number)
    val title = d?.name ?: PhoneText.pretty(number ?: "")
    val history = remember(vm.calls, numbers) {
        vm.calls.filter { e -> numbers.any { PhoneText.sameNumber(it, e.number) } }
    }
    val video = remember { Dialer.supportsVideo(vm.getApplication<android.app.Application>()) }
    var pickNumber by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    var numberMenu by remember { mutableStateOf<String?>(null) }
    var fieldMenu by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var qrText by remember { mutableStateOf<String?>(null) }
    var allHistory by remember { mutableStateOf(false) }
    // Filtr historii z kafelków statystyk: null – wszystkie, inaczej rodzaje połączeń.
    var historyKinds by remember { mutableStateOf<Set<CallKind>?>(null) }
    var noteOpen by remember { mutableStateOf(false) }
    var pickSim by remember { mutableStateOf(false) }
    var linkWith by remember { mutableStateOf(false) }
    var splitRaw by remember { mutableStateOf<RawRef?>(null) }
    val ringtone = rememberLauncherForActivityResult(PickRingtone()) { uri ->
        if (uri != null && d != null) {
            vm.contactsRepo.setRingtone(d.id, uri.takeIf { it != Uri.EMPTY }?.toString())
            reloadKey++
            vm.toast(tr("Dzwonek ustawiony"))
        }
    }
    LaunchedEffect(showQr) {
        if (!showQr || d == null) return@LaunchedEffect
        qrText = withContext(Dispatchers.IO) { runCatching { vm.contactsRepo.draft(d.id) }.getOrNull()?.let { VCard.write(it.copy(note = "")) } }
        if (qrText == null) {
            showQr = false
            vm.toast(tr("Nie udało się przygotować kodu QR"))
        }
    }

    /** Akcja na numerze; przy kilku numerach – najpierw wybór (domyślny numer od razu). */
    fun withNumber(f: (String) -> Unit) {
        val primary = d?.phones?.firstOrNull { it.primary }?.number
        when {
            numbers.isEmpty() -> Unit
            numbers.size == 1 -> f(numbers[0])
            primary != null -> f(primary)
            else -> pickNumber = f
        }
    }

    Page(
        title = "",
        onBack = { vm.pop() },
        actions = {
            if (d != null) {
                RoundIcon(if (d.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (d.starred) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"), active = d.starred) {
                    vm.setStarred(d.id, !d.starred)
                    details = d.copy(starred = !d.starred)
                }
                RoundIcon(Icons.Rounded.Edit, tr("Edytuj")) { vm.editContact(d.id) }
            }
        },
    ) {
        // ---- Nagłówek ---------------------------------------------------------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(d?.name, d?.photoUri, 128.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 32.sp)
            d?.phoneticName?.let { Text(it, fontSize = 13.sp, color = Palette.TextDim, textAlign = TextAlign.Center) }
            d?.nickname?.let { Text("„$it”", fontSize = 14.sp, color = Palette.TextDim, textAlign = TextAlign.Center) }
            val work = listOfNotNull(d?.title, d?.organization, d?.department).joinToString(" · ")
            if (work.isNotBlank()) Text(work, fontSize = 14.sp, color = Palette.TextDim, textAlign = TextAlign.Center)
            if (d == null) {
                val loc = history.firstNotNullOfOrNull { it.location }
                if (loc != null) Text(loc, fontSize = 14.sp, color = Palette.TextDim)
            }
            // Skąd jest kontakt: „Kontakt w Google i w pamięci telefonu” – zwykły tekst, bez plakietek.
            val sources = d?.accountRefs?.map { accountWhere(it) }?.distinct().orEmpty()
            if (sources.isNotEmpty()) {
                Text(
                    tr("Kontakt {0}", joinAnd(sources)),
                    fontSize = 12.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val blocked = numbers.any { vm.isBlocked(it) }
            val badges = blocked || d?.starred == true || d?.sendToVoicemail == true || d?.customRingtone != null
            if (badges) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d?.starred == true) Badge(tr("Ulubiony"), icon = Icons.Rounded.Star)
                    if (blocked) Badge(tr("Zablokowany"), Palette.Record, Icons.Rounded.Block)
                    if (d?.sendToVoicemail == true) Badge(tr("Na pocztę głosową"), icon = Icons.Rounded.Voicemail)
                    if (d?.customRingtone != null) Badge(tr("Własny dzwonek"), icon = Icons.Rounded.MusicNote)
                }
            }
        }

        // ---- Szybkie akcje ----------------------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            BigAction(Icons.Rounded.Call, tr("Zadzwoń"), enabled = numbers.isNotEmpty()) { withNumber { vm.call(it, d?.name) } }
            BigAction(Icons.AutoMirrored.Rounded.Message, tr("SMS"), enabled = numbers.isNotEmpty()) { withNumber { vm.sms(it) } }
            if (video && numbers.isNotEmpty()) BigAction(Icons.Rounded.Videocam, tr("Wideo")) { withNumber { vm.videoCall(it) } }
            if (d != null && d.emails.isNotEmpty()) {
                BigAction(Icons.Rounded.Email, tr("E-mail")) { vm.email((d.emails.firstOrNull { it.primary } ?: d.emails.first()).value) }
            }
            if (d != null && d.addresses.isNotEmpty()) BigAction(Icons.Rounded.Directions, tr("Trasa")) { vm.navigate(d.addresses.first().value) }
            if (d != null && d.ims.isNotEmpty()) {
                val im = d.ims.first()
                BigAction(Icons.Rounded.Chat, Format.imType(im.type, im.label)) { vm.openIm(im.type, im.label, im.value) }
            }
            BigAction(Icons.Rounded.Share, tr("Udostępnij")) {
                if (d != null) vm.shareVCard(d.id) else vm.shareText(number ?: "")
            }
            if (d != null) BigAction(Icons.Rounded.QrCode2, tr("Kod QR")) { showQr = true }
        }

        // ---- Numery -----------------------------------------------------------------------------
        if (numbers.isNotEmpty()) {
            val lastUsed = history.firstOrNull()?.number
            Group(tr("Numery")) {
                if (d != null) {
                    d.phones.forEach { p ->
                        val loc = history.firstOrNull { PhoneText.sameNumber(it.number, p.number) }?.location
                        val parts = listOfNotNull(
                            Format.phoneType(p.type, p.label),
                            tr("domyślny").takeIf { p.primary && d.phones.size > 1 },
                            loc,
                            tr("ostatnio używany").takeIf { d.phones.size > 1 && lastUsed != null && PhoneText.sameNumber(lastUsed, p.number) },
                        )
                        NumberRow(vm, p.number, parts.joinToString(" · "), video, onLong = { numberMenu = p.number })
                    }
                } else {
                    NumberRow(vm, number!!, history.firstNotNullOfOrNull { it.location } ?: tr("Numer"), video, onLong = { numberMenu = number })
                }
                if (d != null && vm.sims.size > 1) {
                    val chosen = vm.settings.contactSim[d.lookupKey]?.let { id -> vm.sims.firstOrNull { it.id == id } }
                    ActionRow(Icons.Rounded.SimCard, tr("Dzwoń z karty"), chosen?.label ?: tr("Jak w ustawieniach")) { pickSim = true }
                }
            }
        }

        if (d != null) {
            if (d.emails.isNotEmpty()) {
                Group(tr("E-mail")) {
                    d.emails.forEach { e ->
                        val sub = Format.emailType(e.type, e.label) + if (e.primary && d.emails.size > 1) " · " + tr("domyślny") else ""
                        ActionRow(Icons.Rounded.Email, e.value, sub, onLongClick = { fieldMenu = e.value }) { vm.email(e.value) }
                    }
                }
            }
            if (d.addresses.isNotEmpty()) {
                Group(tr("Adres")) {
                    d.addresses.forEach { a ->
                        ActionRow(
                            Icons.Rounded.Place, a.value, Format.postalType(a.type, a.label),
                            onLongClick = { fieldMenu = a.value },
                            trailing = { RoundIcon(Icons.Rounded.Directions, tr("Trasa"), size = 36.dp) { vm.navigate(a.value) } },
                        ) { vm.map(a.value) }
                    }
                }
            }
            val work = listOfNotNull(d.organization, d.title, d.department, d.jobDescription)
            if (work.isNotEmpty()) {
                Group(tr("Praca")) {
                    d.organization?.let { ActionRow(Icons.Rounded.Business, it, listOfNotNull(d.title, d.department).joinToString(" · ").ifBlank { tr("Firma") }, onLongClick = { fieldMenu = it }) { fieldMenu = it } }
                    if (d.organization == null && d.title != null) ActionRow(Icons.Rounded.Business, d.title, tr("Stanowisko"), onLongClick = { fieldMenu = d.title }) { fieldMenu = d.title }
                    d.jobDescription?.let { ActionRow(Icons.AutoMirrored.Rounded.Notes, it, tr("Opis stanowiska"), onLongClick = { fieldMenu = it }) { fieldMenu = it } }
                }
            }
            if (d.birthday != null || d.events.isNotEmpty()) {
                Group(tr("Daty")) {
                    d.birthday?.let { b ->
                        val sub = listOfNotNull(tr("Urodziny"), Format.countdown(b, birthday = true)).joinToString(" · ")
                        ActionRow(Icons.Rounded.Cake, Format.eventDate(b), sub, onLongClick = { fieldMenu = Format.eventDate(b) }) { fieldMenu = Format.eventDate(b) }
                    }
                    d.events.forEach { e ->
                        val sub = listOfNotNull(Format.eventType(e.type, e.label), Format.countdown(e.value, birthday = false)).joinToString(" · ")
                        ActionRow(Icons.Rounded.Event, Format.eventDate(e.value), sub, onLongClick = { fieldMenu = Format.eventDate(e.value) }) { fieldMenu = Format.eventDate(e.value) }
                    }
                }
            }
            if (d.relations.isNotEmpty()) {
                Group(tr("Relacje")) {
                    d.relations.forEach { r ->
                        val linked = vm.contacts.firstOrNull { it.id != d.id && it.name.trim().equals(r.value.trim(), ignoreCase = true) }
                        ActionRow(
                            if (linked != null) Icons.Rounded.Person else Icons.Rounded.People,
                            r.value,
                            Format.relationType(r.type, r.label) + if (linked != null) " · " + tr("w kontaktach") else "",
                            onLongClick = { fieldMenu = r.value },
                        ) { if (linked != null) vm.push(Route.ContactInfo(linked.id)) else fieldMenu = r.value }
                    }
                }
            }
            if (d.ims.isNotEmpty() || d.sip.isNotEmpty()) {
                Group(tr("Komunikatory")) {
                    d.ims.forEach { im ->
                        ActionRow(Icons.Rounded.Chat, im.value, Format.imType(im.type, im.label), onLongClick = { fieldMenu = im.value }) { vm.openIm(im.type, im.label, im.value) }
                    }
                    d.sip.forEach { s ->
                        ActionRow(Icons.Rounded.Dialpad, s.value, tr("SIP") + " · " + Format.sipType(s.type, s.label), onLongClick = { fieldMenu = s.value }) { vm.sipCall(s.value) }
                    }
                }
            }
            if (d.websites.isNotEmpty()) {
                Group(tr("Strony WWW")) {
                    d.websites.forEach { w ->
                        ActionRow(Icons.Rounded.Language, w.value, Format.websiteType(w.type, w.label), onLongClick = { fieldMenu = w.value }) { vm.web(w.value) }
                    }
                }
            }
            if (d.groups.isNotEmpty()) {
                Group(tr("Grupy")) {
                    FlowRow(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        d.groups.forEach { Badge(it) }
                    }
                }
            }
            d.note?.let { note ->
                Group(tr("Notatka")) {
                    val long = note.length > 180 || note.lines().size > 4
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .reactive(tr("Notatka"), pressScale = 0.99f, onLongPress = { fieldMenu = note }) { if (long) noteOpen = !noteOpen }
                            .padding(12.dp),
                    ) {
                        Text(
                            note,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            maxLines = if (noteOpen || !long) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (long) Text(if (noteOpen) tr("Zwiń") else tr("Pokaż całość"), fontSize = 12.sp, color = Palette.TextDim, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }

        // ---- Statystyki i historia ------------------------------------------------------------
        if (history.isNotEmpty()) {
            Group(tr("Rozmowy")) {
                val incoming = history.count { it.kind == CallKind.INCOMING }
                val outgoing = history.count { it.kind == CallKind.OUTGOING }
                val missed = history.count { it.kind == CallKind.MISSED || it.kind == CallKind.REJECTED }
                val talk = history.sumOf { it.durationSec }
                val answered = history.filter { it.durationSec > 0 }
                val inKinds = setOf(CallKind.INCOMING)
                val outKinds = setOf(CallKind.OUTGOING)
                val missedKinds = setOf(CallKind.MISSED, CallKind.REJECTED)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Dotknięcie kafelka zawęża historię poniżej do tego rodzaju; ponowne – pokazuje wszystkie.
                    StatTile(history.size.toString(), tr("połączeń"), Modifier.weight(1f), selected = historyKinds == null) { historyKinds = null }
                    StatTile(incoming.toString(), tr("przychodzące"), Modifier.weight(1f), selected = historyKinds == inKinds) {
                        historyKinds = if (historyKinds == inKinds) null else inKinds
                    }
                    StatTile(outgoing.toString(), tr("wychodzące"), Modifier.weight(1f), selected = historyKinds == outKinds) {
                        historyKinds = if (historyKinds == outKinds) null else outKinds
                    }
                    StatTile(missed.toString(), tr("nieodebrane"), Modifier.weight(1f), danger = missed > 0, selected = historyKinds == missedKinds) {
                        historyKinds = if (historyKinds == missedKinds) null else missedKinds
                    }
                }
                val lines = listOfNotNull(
                    tr("Łącznie rozmów: {0}", PhoneText.durationShort(talk, tr("h"), tr("min"), tr("s"))).takeIf { talk > 0 },
                    tr("Średnio: {0}", PhoneText.durationShort(talk / answered.size, tr("h"), tr("min"), tr("s"))).takeIf { answered.isNotEmpty() },
                    tr("Ostatni kontakt: {0}", Format.relative(history.first().date)),
                )
                Text(
                    lines.joinToString(" · "),
                    fontSize = 12.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
                val shownHistory = historyKinds?.let { k -> history.filter { it.kind in k } } ?: history
                if (shownHistory.isEmpty()) {
                    Text(tr("Brak połączeń tego rodzaju"), fontSize = 13.sp, color = Palette.TextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
                val visible = if (allHistory) shownHistory else shownHistory.take(HISTORY_PREVIEW)
                visible.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        val isMissed = e.kind == CallKind.MISSED || e.kind == CallKind.REJECTED
                        Icon(kindIcon(e.kind), null, tint = if (isMissed) Palette.Record else Palette.TextDim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Format.kind(e.kind) + if (e.video) " · " + tr("wideo") else "", fontSize = 14.sp, color = if (isMissed) Palette.Record else Palette.Fg)
                            Text(Format.full(e.date) + if (numbers.size > 1) " · " + PhoneText.pretty(e.number) else "", fontSize = 11.sp, color = Palette.TextDim)
                        }
                        if (e.durationSec > 0) Text(PhoneText.duration(e.durationSec), fontSize = 13.sp, color = Palette.TextDim)
                    }
                }
                if (shownHistory.size > HISTORY_PREVIEW) {
                    Text(
                        if (allHistory) tr("Zwiń historię") else tr("Pokaż wszystkie ({0})", shownHistory.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .reactive(tr("Historia połączeń"), pressScale = 0.98f) { allHistory = !allHistory }
                            .padding(12.dp),
                    )
                }
            }
        }

        // ---- Połączone kontakty -------------------------------------------------------------------
        if (d != null) {
            Group(tr("Połączone kontakty")) {
                if (raws.size > 1) {
                    raws.forEach { r ->
                        ActionRow(
                            accountIcon(r.account),
                            r.name.ifBlank { d.name },
                            accountBadge(r.account) + (r.account.name?.takeIf { !r.account.local && !r.account.sim && it != accountBadge(r.account) }?.let { " · $it" } ?: ""),
                            trailing = { RoundIcon(Icons.AutoMirrored.Rounded.CallSplit, tr("Rozłącz"), size = 36.dp) { splitRaw = r } },
                        ) { splitRaw = r }
                    }
                } else {
                    Text(
                        tr("Kontakt pochodzi z jednego źródła: {0}.", raws.firstOrNull()?.account?.let { accountLabel(it) } ?: tr("Telefon")),
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                ActionRow(Icons.Rounded.Link, tr("Połącz z innym kontaktem"), tr("Np. ta sama osoba zapisana dwa razy")) { linkWith = true }
            }
        }

        // ---- Opcje --------------------------------------------------------------------------------
        Group(tr("Opcje")) {
            if (d != null) {
                ActionRow(Icons.Rounded.MusicNote, tr("Dzwonek"), if (d.customRingtone != null) ringtoneName(vm, d.customRingtone) else tr("Domyślny")) {
                    ringtone.launch(d.customRingtone)
                }
                SwitchRow(Icons.Rounded.Voicemail, tr("Kieruj na pocztę głosową"), tr("Połączenia od tej osoby nie będą dzwonić"), d.sendToVoicemail) { on ->
                    vm.contactsRepo.setSendToVoicemail(d.id, on)
                    details = d.copy(sendToVoicemail = on)
                }
                ActionRow(Icons.Rounded.Share, tr("Udostępnij kontakt"), tr("Jako plik vCard")) { vm.shareVCard(d.id) }
                ActionRow(Icons.Rounded.QrCode2, tr("Kod QR"), tr("Do zeskanowania drugim telefonem")) { showQr = true }
            } else if (number != null) {
                ActionRow(Icons.Rounded.PersonAdd, tr("Utwórz kontakt")) { vm.newContact(number) }
                ActionRow(Icons.Rounded.Person, tr("Dodaj do istniejącego kontaktu")) { addTo = true }
                ActionRow(Icons.Rounded.ContentCopy, tr("Kopiuj numer")) { vm.copy(number) }
            }
            numbers.forEach { n ->
                val blocked = vm.isBlocked(n)
                ActionRow(
                    Icons.Rounded.Block,
                    if (blocked) tr("Odblokuj") else tr("Zablokuj"),
                    if (numbers.size > 1) PhoneText.pretty(n) else null,
                    danger = !blocked,
                ) { if (blocked) vm.unblock(n) else vm.block(n) }
            }
            if (history.isNotEmpty()) {
                ActionRow(Icons.Rounded.Delete, tr("Usuń historię połączeń"), tr("{0} wpisów", history.size), danger = true) { vm.deleteCalls(history.map { it.id }) }
            }
            if (d != null) {
                ActionRow(Icons.Rounded.Delete, tr("Usuń kontakt"), d.accounts.joinToString(", ").ifBlank { null }, danger = true) { confirmDelete = true }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    // ---- Okna -------------------------------------------------------------------------------------

    pickNumber?.let { f ->
        ChoiceDialog(
            tr("Wybierz numer"),
            d?.phones?.map { Icons.Rounded.Call to "${Format.phoneType(it.type, it.label)} · ${PhoneText.pretty(it.number)}" }.orEmpty(),
            onDismiss = { pickNumber = null },
        ) { i ->
            pickNumber = null
            f(numbers[i])
        }
    }
    numberMenu?.let { n ->
        val canPrimary = d != null && d.phones.size > 1
        ChoiceDialog(
            PhoneText.pretty(n),
            buildList {
                add(Icons.Rounded.ContentCopy to tr("Kopiuj numer"))
                add(Icons.Rounded.Share to tr("Udostępnij"))
                add(Icons.Rounded.Call to tr("Edytuj przed połączeniem"))
                if (canPrimary) add(Icons.Rounded.Star to tr("Ustaw jako domyślny"))
            },
            onDismiss = { numberMenu = null },
        ) { i ->
            numberMenu = null
            when (i) {
                0 -> vm.copy(n)
                1 -> vm.shareText(PhoneText.pretty(n))
                2 -> {
                    vm.dialed = PhoneText.digits(n)
                    vm.stack.clear()
                    vm.openKeypad()
                }
                3 -> if (d != null) {
                    vm.contactsRepo.setPrimaryNumber(d.id, n)
                    reloadKey++
                }
            }
        }
    }
    fieldMenu?.let { v -> FieldMenu(vm, v) { fieldMenu = null } }
    if (showQr && d != null) {
        val text = qrText
        if (text != null) {
            QrDialog(d.name, text) {
                showQr = false
                qrText = null
            }
        }
    }
    if (pickSim && d != null) {
        ChoiceDialog(
            tr("Dzwoń z karty"),
            listOf(Icons.Rounded.SimCard to tr("Jak w ustawieniach")) + vm.sims.map { Icons.Rounded.SimCard to (it.label + (it.number?.let { n -> " · " + PhoneText.pretty(n) } ?: "")) },
            onDismiss = { pickSim = false },
            subtitle = tr("Karta zapamiętana dla tej osoby, także przy dzwonieniu z listy."),
        ) { i ->
            pickSim = false
            vm.setContactSim(d.lookupKey, if (i == 0) null else vm.sims[i - 1].id)
        }
    }
    if (linkWith && d != null) {
        val list = vm.contacts.filter { it.id != d.id }.take(300)
        ChoiceDialog(tr("Połącz z kontaktem"), list.map { Icons.Rounded.Person to it.name }, onDismiss = { linkWith = false }, subtitle = tr("Oba wpisy będą widoczne jako jeden kontakt. Dane nie są usuwane.")) { i ->
            linkWith = false
            vm.mergeContacts(listOf(d.id, list[i].id))
            reloadKey++
        }
    }
    splitRaw?.let { r ->
        if (d == null) return@let
        DarkDialog(
            tr("Rozłączyć „{0}”?", accountBadge(r.account)),
            onDismiss = { splitRaw = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { splitRaw = null }
                PillButton(tr("Rozłącz")) {
                    splitRaw = null
                    vm.splitContact(d.id, r.id) { reloadKey++ }
                }
            },
        ) {
            Text(tr("Ten wpis stanie się osobnym kontaktem. Nic nie zostanie usunięte."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
    if (addTo && number != null) {
        val sorted = vm.contacts
        ChoiceDialog(tr("Dodaj do kontaktu"), sorted.take(200).map { Icons.Rounded.Person to it.name }, onDismiss = { addTo = false }, subtitle = tr("Numer {0}", PhoneText.pretty(number))) { i ->
            addTo = false
            vm.addNumberTo(sorted[i].id, number)
        }
    }
    if (confirmDelete && d != null) {
        DarkDialog(
            tr("Usunąć {0}?", d.name),
            onDismiss = { confirmDelete = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { confirmDelete = false }
                PillButton(tr("Usuń"), danger = true) {
                    confirmDelete = false
                    vm.deleteContacts(listOf(d.id))
                    vm.pop()
                }
            },
        ) {
            Text(tr("Kontakt zniknie z telefonu i ze wszystkich zsynchronizowanych kont."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NumberRow(vm: AppViewModel, number: String, label: String, video: Boolean, onLong: () -> Unit) {
    ActionRow(
        Icons.Rounded.Call,
        PhoneText.pretty(number),
        label,
        onLongClick = onLong,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (video) RoundIcon(Icons.Rounded.Videocam, tr("Wideo"), size = 36.dp) { vm.videoCall(number) }
                RoundIcon(Icons.AutoMirrored.Rounded.Message, tr("SMS"), size = 36.dp) { vm.sms(number) }
            }
        },
    ) { vm.call(number) }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, danger: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val fg = when {
        selected -> Palette.Ink
        danger -> Palette.Record
        else -> Palette.Fg
    }
    Column(
        modifier
            .reactive(label, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.Fg else Palette.Fg.copy(alpha = 0.06f))
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(label, fontSize = 10.sp, color = if (selected) Palette.Ink else Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .reactive(label, enabled, onClick = onClick)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Fg.copy(alpha = if (enabled) 0.09f else 0.04f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) Palette.Fg else Palette.TextFaint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
