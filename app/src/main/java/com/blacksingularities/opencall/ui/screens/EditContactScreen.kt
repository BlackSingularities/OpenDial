package com.blacksingularities.opencall.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.AccountRef
import com.blacksingularities.opencall.data.ContactDraft
import com.blacksingularities.opencall.data.ContactKinds
import com.blacksingularities.opencall.data.DraftField
import com.blacksingularities.opencall.data.GroupRef
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.PostalField
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.PhotoCropper
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.components.SwitchRow
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sekcje formularza – rzadziej używane pojawiają się dopiero po wypełnieniu albo „Dodaj pole”. */
private enum class Sec { NAME, PHONE, EMAIL, ADDRESS, WORK, EVENTS, RELATIONS, IM, SIP, WEB, GROUPS, NOTE, OPTIONS }

/** Pozycja menu („Dodaj pole”, zdjęcie). */
private class Opt(val icon: ImageVector, val label: String, val run: () -> Unit)

/** Pole, dla którego wybierany jest rodzaj. */
private enum class Kind { PHONE, EMAIL, ADDRESS, WEB, EVENT, RELATION, SIP, IM }

private fun <T> List<T>.replaced(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
private fun <T> List<T>.without(i: Int): List<T> = toMutableList().also { it.removeAt(i) }
private fun <T> List<T>.swapped(i: Int, j: Int): List<T> = toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }

/** Ten sam rodzaj co poprzednie pole albo pierwszy z listy – „Dodaj numer” po komórce daje dom itd. */
private fun nextType(used: List<Int>, all: List<Int>): Int = all.firstOrNull { it !in used } ?: all.first()

private fun initialSections(d: ContactDraft): List<Sec> = buildList {
    add(Sec.NAME)
    add(Sec.PHONE)
    add(Sec.EMAIL)
    if (d.addresses.isNotEmpty()) add(Sec.ADDRESS)
    if (listOf(d.organization, d.title, d.department, d.jobDescription).any { it.isNotBlank() }) add(Sec.WORK)
    if (d.birthday.isNotBlank() || d.events.isNotEmpty()) add(Sec.EVENTS)
    if (d.relations.isNotEmpty()) add(Sec.RELATIONS)
    if (d.ims.isNotEmpty()) add(Sec.IM)
    if (d.sip.isNotEmpty()) add(Sec.SIP)
    if (d.websites.isNotEmpty()) add(Sec.WEB)
    if (d.groups.isNotEmpty()) add(Sec.GROUPS)
    if (d.note.isNotBlank()) add(Sec.NOTE)
    add(Sec.OPTIONS)
}

/**
 * Formularz kontaktu: nazwa (z członami i zapisem fonetycznym), numery, e-maile, adresy w polach,
 * firma, wydarzenia, relacje, komunikatory, SIP, strony, grupy, notatka, zdjęcie i opcje.
 * Rzadkie sekcje dochodzą przyciskiem „Dodaj pole”; każdą sekcję można zwinąć.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditContactScreen(vm: AppViewModel, initial: ContactDraft) {
    var d by remember(initial) { mutableStateOf(initial) }
    var fullName by remember {
        mutableStateOf(listOf(initial.prefix, initial.middle, initial.suffix, initial.nickname, initial.phoneticName).any { it.isNotBlank() })
    }
    val shown = remember { mutableStateListOf<Sec>().apply { addAll(initialSections(initial)) } }
    val collapsed = remember { mutableStateMapOf<Sec, Boolean>() }
    var typeFor by remember { mutableStateOf<Pair<Kind, Int>?>(null) }
    var dateFor by remember { mutableStateOf<Int?>(null) } // -1 = urodziny, ≥ 0 = wydarzenie
    var relationPick by remember { mutableStateOf<Int?>(null) }
    var imPickNew by remember { mutableStateOf(false) }
    var addField by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var confirmErrors by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    var focusKey by remember { mutableStateOf<String?>(null) }
    val accounts = remember { runCatching { vm.contactsRepo.accounts() }.getOrDefault(emptyList()) }
    var groups by remember { mutableStateOf<List<GroupRef>>(emptyList()) }
    var groupsKey by remember { mutableStateOf(0) }
    LaunchedEffect(d.account, groupsKey) {
        groups = withContext(Dispatchers.IO) { runCatching { vm.contactsRepo.groups(d.account) }.getOrDefault(emptyList()) }
    }

    val isNew = d.id == null
    val existingPhoto = remember(d.id) { if (isNew) null else vm.contacts.firstOrNull { it.id == d.id }?.photoUri }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var lastSource by remember { mutableStateOf<Bitmap?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pickedUri = uri }
    val cameraUri = remember {
        runCatching {
            val app = vm.getApplication<android.app.Application>()
            val dir = java.io.File(app.cacheDir, "share").apply { mkdirs() }
            androidx.core.content.FileProvider.getUriForFile(app, app.packageName + ".files", java.io.File(dir, "camera.jpg"))
        }.getOrNull()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) pickedUri = cameraUri }
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        cropSource = withContext(Dispatchers.IO) { vm.contactsRepo.loadPhotoForCrop(uri) }
        if (cropSource == null) vm.toast(tr("Nie udało się wczytać zdjęcia")) else lastSource = cropSource
        pickedUri = null
    }
    val ringtone = rememberLauncherForActivityResult(PickRingtone()) { uri ->
        if (uri != null) d = d.copy(ringtone = uri.takeIf { it != Uri.EMPTY }?.toString())
    }

    val changed = d != initial
    // Karta SIM mieści tylko nazwę i jeden numer – formularz pokazuje wtedy tylko te pola.
    val onSim = d.account?.sim == true
    val simCard = remember(d.account) { d.account?.simSubId?.let { sub -> runCatching { vm.contactsRepo.sim.cards() }.getOrNull()?.firstOrNull { it.subscriptionId == sub } } }

    val phoneErrors = d.phones.map { !ContactKinds.validPhone(it.value) }
    val emailErrors = d.emails.map { !ContactKinds.validEmail(it.value) }
    val hasErrors = phoneErrors.any { it } || emailErrors.any { it }

    fun show(s: Sec) {
        if (s !in shown) shown.add(s)
        collapsed[s] = false
    }

    fun doSave() {
        vm.saveContact(d) { id ->
            vm.pop()
            if (id == null) return@saveContact
            when {
                isNew -> vm.push(Route.ContactInfo(id))
                // Przeniesiony kontakt dostaje nowe id – ekran szczegółów pod spodem pokazuje już nowy.
                id != d.id && vm.stack.lastOrNull() == Route.ContactInfo(d.id!!) -> vm.replaceTop(Route.ContactInfo(id))
            }
        }
    }

    fun save() {
        if (d.isEmpty) {
            vm.toast(tr("Wpisz nazwę albo numer"))
            return
        }
        if (hasErrors) confirmErrors = true else doSave()
    }

    androidx.activity.compose.BackHandler(enabled = changed) { discard = true }

    cropSource?.let { src ->
        PhotoCropper(src, onCancel = { cropSource = null }) { bmp, left, top, side ->
            vm.contactsRepo.cropPhoto(bmp, left, top, side)?.let { d = d.copy(photo = it, photoChanged = true) }
            cropSource = null
        }
    }

    val hasPhoto = d.photo != null || (!d.photoChanged && existingPhoto != null)

    Page(
        if (isNew) tr("Nowy kontakt") else tr("Edytuj kontakt"),
        onBack = { if (changed) discard = true else vm.pop() },
        actions = { PillButton(if (vm.simSaving) tr("Zapisywanie…") else tr("Zapisz"), icon = Icons.Rounded.Check, enabled = !vm.simSaving, onClick = ::save) },
    ) {
        if (!onSim) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .reactive(tr("Zdjęcie kontaktu")) { photoMenu = true }
                    .size(112.dp),
            ) {
                val photo = d.photo
                val bmp = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Avatar(d.displayName.ifBlank { d.organization }, if (d.photoChanged) null else existingPhoto, 112.dp)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(Palette.Fg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
                }
            }
            if (d.displayName.isNotBlank()) {
                Text(d.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
        }

        val accountChoices = if (isNew) accounts else accounts.filter { !it.sim }
        if (!onSim || isNew) {
            if (accountChoices.size > 1) {
                val acc = d.account
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Fg.copy(alpha = 0.06f))
                        .reactive(tr("Zapisz w"), pressScale = 0.98f) { pickAccount = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(accountIcon(acc), null, tint = Palette.TextDim)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isNew) tr("Zapisz w") else tr("Konto"), fontSize = 11.sp, color = Palette.TextDim)
                        Text(accountLabel(acc), fontSize = 14.sp)
                    }
                    Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextDim)
                }
            }
            if (d.moving) {
                Text(
                    tr("Kontakt zostanie przeniesiony z „{0}” do „{1}”. Pola, których OpenDial nie obsługuje, nie przejdą do nowego konta.", accountLabel(d.originalAccount), accountLabel(d.account)),
                    fontSize = 12.sp,
                    color = Palette.TextDim,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (onSim) {
            SimFields(vm, d, simCard) { d = it }
        }

        if (!onSim) {
            EditSection(Sec.NAME, tr("Nazwa"), Icons.Rounded.Person, collapsed, summary = d.displayName) {
                if (fullName) Field(d.prefix, { d = d.copy(prefix = it) }, tr("Tytuł (np. dr)"), cap = KeyboardCapitalization.Words)
                Field(d.given, { d = d.copy(given = it) }, tr("Imię"), Icons.Rounded.Person, cap = KeyboardCapitalization.Words, focus = focusKey == "given", onFocused = { focusKey = null })
                if (fullName) Field(d.middle, { d = d.copy(middle = it) }, tr("Drugie imię"), cap = KeyboardCapitalization.Words)
                Field(d.family, { d = d.copy(family = it) }, tr("Nazwisko"), cap = KeyboardCapitalization.Words)
                if (fullName) {
                    Field(d.suffix, { d = d.copy(suffix = it) }, tr("Przyrostek"))
                    Field(d.nickname, { d = d.copy(nickname = it) }, tr("Pseudonim"), Icons.Rounded.Badge, cap = KeyboardCapitalization.Words, focus = focusKey == "nickname", onFocused = { focusKey = null })
                    Field(d.phoneticGiven, { d = d.copy(phoneticGiven = it) }, tr("Imię – wymowa"), Icons.Rounded.RecordVoiceOver, focus = focusKey == "phonetic", onFocused = { focusKey = null })
                    Field(d.phoneticMiddle, { d = d.copy(phoneticMiddle = it) }, tr("Drugie imię – wymowa"))
                    Field(d.phoneticFamily, { d = d.copy(phoneticFamily = it) }, tr("Nazwisko – wymowa"))
                } else {
                    LinkText(tr("Więcej pól nazwy")) { fullName = true }
                }
            }

            EditSection(Sec.PHONE, tr("Telefon"), Icons.Rounded.Call, collapsed, count = d.phones.count { it.value.isNotBlank() }) {
                d.phones.forEachIndexed { i, p ->
                    val other = remember(p.value, vm.contacts) {
                        if (PhoneText.digits(p.value).length >= 6) vm.contactFor(p.value)?.takeIf { it.id != d.id } else null
                    }
                    FieldCard(
                        value = p.value,
                        onValue = { v -> d = d.copy(phones = d.phones.replaced(i, p.copy(value = v))) },
                        label = tr("Numer"),
                        icon = Icons.Rounded.Call,
                        keyboard = KeyboardType.Phone,
                        transformation = PhoneFormatTransformation,
                        type = Format.phoneType(p.type, p.label),
                        onType = { typeFor = Kind.PHONE to i },
                        primary = if (d.phones.size > 1) p.primary else null,
                        onPrimary = { d = d.copy(phones = d.phones.mapIndexed { j, f -> f.copy(primary = j == i && !p.primary) }) },
                        onUp = if (i > 0) ({ d = d.copy(phones = d.phones.swapped(i, i - 1)) }) else null,
                        onDown = if (i < d.phones.lastIndex) ({ d = d.copy(phones = d.phones.swapped(i, i + 1)) }) else null,
                        onRemove = { d = d.copy(phones = d.phones.without(i)) },
                        error = if (phoneErrors[i]) tr("Ten numer wygląda na błędny") else null,
                        focus = focusKey == "phone:$i",
                        onFocused = { focusKey = null },
                    ) {
                        if (other != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                                Text(tr("Ten numer ma już kontakt {0}", other.name), fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
                                LinkText(tr("Otwórz")) { vm.push(Route.ContactInfo(other.id)) }
                            }
                        }
                    }
                }
                AddRow(tr("Dodaj numer")) {
                    d = d.copy(phones = d.phones + DraftField("", nextType(d.phones.map { it.type }, Format.phoneTypes)))
                    focusKey = "phone:${d.phones.lastIndex}"
                }
            }

            EditSection(Sec.EMAIL, tr("E-mail"), Icons.Rounded.Email, collapsed, count = d.emails.count { it.value.isNotBlank() }) {
                d.emails.forEachIndexed { i, e ->
                    FieldCard(
                        value = e.value,
                        onValue = { v -> d = d.copy(emails = d.emails.replaced(i, e.copy(value = v.trim()))) },
                        label = tr("Adres e-mail"),
                        icon = Icons.Rounded.Email,
                        keyboard = KeyboardType.Email,
                        type = Format.emailType(e.type, e.label),
                        onType = { typeFor = Kind.EMAIL to i },
                        primary = if (d.emails.size > 1) e.primary else null,
                        onPrimary = { d = d.copy(emails = d.emails.mapIndexed { j, f -> f.copy(primary = j == i && !e.primary) }) },
                        onUp = if (i > 0) ({ d = d.copy(emails = d.emails.swapped(i, i - 1)) }) else null,
                        onDown = if (i < d.emails.lastIndex) ({ d = d.copy(emails = d.emails.swapped(i, i + 1)) }) else null,
                        onRemove = { d = d.copy(emails = d.emails.without(i)) },
                        error = if (emailErrors[i]) tr("Niepełny adres e-mail") else null,
                        focus = focusKey == "email:$i",
                        onFocused = { focusKey = null },
                    )
                }
                AddRow(tr("Dodaj e-mail")) {
                    d = d.copy(emails = d.emails + DraftField("", nextType(d.emails.map { it.type }, Format.emailTypes)))
                    focusKey = "email:${d.emails.lastIndex}"
                }
            }

            if (Sec.ADDRESS in shown) EditSection(Sec.ADDRESS, tr("Adres"), Icons.Rounded.Place, collapsed, count = d.addresses.size) {
                d.addresses.forEachIndexed { i, a ->
                    AddressCard(
                        a,
                        onChange = { v -> d = d.copy(addresses = d.addresses.replaced(i, v)) },
                        onType = { typeFor = Kind.ADDRESS to i },
                        onRemove = { d = d.copy(addresses = d.addresses.without(i)) },
                        focus = focusKey == "address:$i",
                        onFocused = { focusKey = null },
                    )
                }
                AddRow(tr("Dodaj adres")) {
                    d = d.copy(addresses = d.addresses + PostalField(type = nextType(d.addresses.map { it.type }, ContactKinds.postalTypes)))
                    focusKey = "address:${d.addresses.lastIndex}"
                }
            }

            if (Sec.WORK in shown) EditSection(Sec.WORK, tr("Praca"), Icons.Rounded.Business, collapsed, summary = listOf(d.organization, d.title).filter { it.isNotBlank() }.joinToString(" · ")) {
                Field(d.organization, { d = d.copy(organization = it) }, tr("Firma"), Icons.Rounded.Business, cap = KeyboardCapitalization.Words, focus = focusKey == "org", onFocused = { focusKey = null })
                Field(d.title, { d = d.copy(title = it) }, tr("Stanowisko"), cap = KeyboardCapitalization.Sentences)
                Field(d.department, { d = d.copy(department = it) }, tr("Dział"), cap = KeyboardCapitalization.Sentences)
                Field(d.jobDescription, { d = d.copy(jobDescription = it) }, tr("Opis stanowiska"), cap = KeyboardCapitalization.Sentences, singleLine = false)
            }

            if (Sec.EVENTS in shown) EditSection(Sec.EVENTS, tr("Daty"), Icons.Rounded.Event, collapsed, count = d.events.size + if (d.birthday.isNotBlank()) 1 else 0) {
                if (d.birthday.isNotBlank()) {
                    DateCard(Icons.Rounded.Cake, tr("Urodziny"), d.birthday, birthday = true, onClick = { dateFor = -1 }, onType = null) { d = d.copy(birthday = "") }
                }
                d.events.forEachIndexed { i, e ->
                    DateCard(
                        Icons.Rounded.Event,
                        Format.eventType(e.type, e.label),
                        e.value,
                        birthday = false,
                        onClick = { dateFor = i },
                        onType = { typeFor = Kind.EVENT to i },
                    ) { d = d.copy(events = d.events.without(i)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (d.birthday.isBlank()) AddRow(tr("Urodziny")) { dateFor = -1 }
                    AddRow(tr("Inna data")) {
                        d = d.copy(events = d.events + DraftField("", nextType(d.events.map { it.type }, ContactKinds.eventTypes)))
                        dateFor = d.events.lastIndex
                    }
                }
            }

            if (Sec.RELATIONS in shown) EditSection(Sec.RELATIONS, tr("Relacje"), Icons.Rounded.People, collapsed, count = d.relations.size) {
                d.relations.forEachIndexed { i, r ->
                    FieldCard(
                        value = r.value,
                        onValue = { v -> d = d.copy(relations = d.relations.replaced(i, r.copy(value = v))) },
                        label = tr("Imię i nazwisko"),
                        icon = Icons.Rounded.People,
                        cap = KeyboardCapitalization.Words,
                        type = Format.relationType(r.type, r.label),
                        onType = { typeFor = Kind.RELATION to i },
                        onRemove = { d = d.copy(relations = d.relations.without(i)) },
                        focus = focusKey == "relation:$i",
                        onFocused = { focusKey = null },
                        extraAction = { RoundIcon(Icons.Rounded.Contacts, tr("Wybierz z kontaktów"), size = 32.dp) { relationPick = i } },
                    )
                }
                AddRow(tr("Dodaj relację")) {
                    d = d.copy(relations = d.relations + DraftField("", nextType(d.relations.map { it.type }, ContactKinds.relationTypes)))
                    focusKey = "relation:${d.relations.lastIndex}"
                }
            }

            if (Sec.IM in shown) EditSection(Sec.IM, tr("Komunikatory"), Icons.Rounded.Chat, collapsed, count = d.ims.size) {
                d.ims.forEachIndexed { i, im ->
                    FieldCard(
                        value = im.value,
                        onValue = { v -> d = d.copy(ims = d.ims.replaced(i, im.copy(value = v.trim()))) },
                        label = tr("Numer albo nazwa użytkownika"),
                        icon = Icons.Rounded.Chat,
                        keyboard = KeyboardType.Email,
                        type = Format.imType(im.type, im.label),
                        onType = { typeFor = Kind.IM to i },
                        onRemove = { d = d.copy(ims = d.ims.without(i)) },
                        focus = focusKey == "im:$i",
                        onFocused = { focusKey = null },
                    )
                }
                AddRow(tr("Dodaj komunikator")) { imPickNew = true }
            }

            if (Sec.SIP in shown) EditSection(Sec.SIP, tr("Adres SIP"), Icons.Rounded.Dialpad, collapsed, count = d.sip.size) {
                d.sip.forEachIndexed { i, s ->
                    FieldCard(
                        value = s.value,
                        onValue = { v -> d = d.copy(sip = d.sip.replaced(i, s.copy(value = v.trim()))) },
                        label = tr("Adres SIP (np. jan@sip.example.com)"),
                        icon = Icons.Rounded.Dialpad,
                        keyboard = KeyboardType.Email,
                        type = Format.sipType(s.type, s.label),
                        onType = { typeFor = Kind.SIP to i },
                        onRemove = { d = d.copy(sip = d.sip.without(i)) },
                        focus = focusKey == "sip:$i",
                        onFocused = { focusKey = null },
                    )
                }
                AddRow(tr("Dodaj adres SIP")) {
                    d = d.copy(sip = d.sip + DraftField("", nextType(d.sip.map { it.type }, ContactKinds.sipTypes)))
                    focusKey = "sip:${d.sip.lastIndex}"
                }
            }

            if (Sec.WEB in shown) EditSection(Sec.WEB, tr("Strony WWW"), Icons.Rounded.Language, collapsed, count = d.websites.size) {
                d.websites.forEachIndexed { i, w ->
                    FieldCard(
                        value = w.value,
                        onValue = { v -> d = d.copy(websites = d.websites.replaced(i, w.copy(value = v.trim()))) },
                        label = tr("Strona WWW"),
                        icon = Icons.Rounded.Language,
                        keyboard = KeyboardType.Uri,
                        type = Format.websiteType(w.type, w.label),
                        onType = { typeFor = Kind.WEB to i },
                        onUp = if (i > 0) ({ d = d.copy(websites = d.websites.swapped(i, i - 1)) }) else null,
                        onDown = if (i < d.websites.lastIndex) ({ d = d.copy(websites = d.websites.swapped(i, i + 1)) }) else null,
                        onRemove = { d = d.copy(websites = d.websites.without(i)) },
                        focus = focusKey == "web:$i",
                        onFocused = { focusKey = null },
                    )
                }
                AddRow(tr("Dodaj stronę")) {
                    d = d.copy(websites = d.websites + DraftField("", nextType(d.websites.map { it.type }, ContactKinds.websiteTypes)))
                    focusKey = "web:${d.websites.lastIndex}"
                }
            }

            if (Sec.GROUPS in shown) EditSection(Sec.GROUPS, tr("Grupy"), Icons.Rounded.Group, collapsed, count = d.groups.size) {
                if (groups.isEmpty()) {
                    Text(tr("To konto nie ma jeszcze grup."), fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(4.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        val on = g.id in d.groups
                        SelectChip(g.title, on) { d = d.copy(groups = if (on) d.groups - g.id else d.groups + g.id) }
                    }
                    AddRow(tr("Nowa grupa")) { newGroup = true }
                }
            }

            if (Sec.NOTE in shown) EditSection(Sec.NOTE, tr("Notatka"), Icons.AutoMirrored.Rounded.Notes, collapsed, summary = d.note.lines().firstOrNull().orEmpty()) {
                Field(d.note, { d = d.copy(note = it) }, tr("Notatka"), Icons.AutoMirrored.Rounded.Notes, singleLine = false, minLines = 3, cap = KeyboardCapitalization.Sentences, focus = focusKey == "note", onFocused = { focusKey = null })
            }

            EditSection(Sec.OPTIONS, tr("Opcje"), Icons.Rounded.Tune, collapsed, summary = listOfNotNull(
                tr("Ulubiony").takeIf { d.starred },
                tr("Własny dzwonek").takeIf { d.ringtone != null },
                tr("Poczta głosowa").takeIf { d.sendToVoicemail },
            ).joinToString(" · ")) {
                SwitchRow(if (d.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Ulubiony"), tr("Pokazuj w Ulubionych"), d.starred) { d = d.copy(starred = it) }
                ActionRow(Icons.Rounded.MusicNote, tr("Dzwonek"), d.ringtone?.let { ringtoneName(vm, it) } ?: tr("Domyślny")) { ringtone.launch(d.ringtone) }
                SwitchRow(Icons.Rounded.Voicemail, tr("Kieruj na pocztę głosową"), tr("Połączenia od tej osoby nie będą dzwonić"), d.sendToVoicemail) { d = d.copy(sendToVoicemail = it) }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton(tr("Dodaj pole"), primary = false, icon = Icons.Rounded.Add, modifier = Modifier.padding(top = 4.dp)) { addField = true }
            }
        }
        Spacer(Modifier.size(24.dp))
    }

    // ---- Okna -----------------------------------------------------------------------------------

    if (addField) {
        val options = buildList<Opt> {
            add(Opt(Icons.Rounded.Call, tr("Numer telefonu")) {
                show(Sec.PHONE)
                d = d.copy(phones = d.phones + DraftField("", nextType(d.phones.map { it.type }, Format.phoneTypes)))
                focusKey = "phone:${d.phones.lastIndex}"
            })
            add(Opt(Icons.Rounded.Email, tr("E-mail")) {
                show(Sec.EMAIL)
                d = d.copy(emails = d.emails + DraftField("", nextType(d.emails.map { it.type }, Format.emailTypes)))
                focusKey = "email:${d.emails.lastIndex}"
            })
            add(Opt(Icons.Rounded.Place, tr("Adres")) {
                show(Sec.ADDRESS)
                d = d.copy(addresses = d.addresses + PostalField(type = nextType(d.addresses.map { it.type }, ContactKinds.postalTypes)))
                focusKey = "address:${d.addresses.lastIndex}"
            })
            if (Sec.WORK !in shown) add(Opt(Icons.Rounded.Business, tr("Firma i stanowisko")) {
                show(Sec.WORK)
                focusKey = "org"
            })
            if (d.birthday.isBlank()) add(Opt(Icons.Rounded.Cake, tr("Urodziny")) {
                show(Sec.EVENTS)
                dateFor = -1
            })
            add(Opt(Icons.Rounded.Event, tr("Rocznica albo inna data")) {
                show(Sec.EVENTS)
                d = d.copy(events = d.events + DraftField("", nextType(d.events.map { it.type }, ContactKinds.eventTypes)))
                dateFor = d.events.lastIndex
            })
            add(Opt(Icons.Rounded.People, tr("Relacja (małżonek, dziecko…)")) {
                show(Sec.RELATIONS)
                d = d.copy(relations = d.relations + DraftField("", nextType(d.relations.map { it.type }, ContactKinds.relationTypes)))
                focusKey = "relation:${d.relations.lastIndex}"
            })
            add(Opt(Icons.Rounded.Chat, tr("Komunikator")) { imPickNew = true })
            add(Opt(Icons.Rounded.Dialpad, tr("Adres SIP")) {
                show(Sec.SIP)
                d = d.copy(sip = d.sip + DraftField("", nextType(d.sip.map { it.type }, ContactKinds.sipTypes)))
                focusKey = "sip:${d.sip.lastIndex}"
            })
            add(Opt(Icons.Rounded.Language, tr("Strona WWW")) {
                show(Sec.WEB)
                d = d.copy(websites = d.websites + DraftField("", nextType(d.websites.map { it.type }, ContactKinds.websiteTypes)))
                focusKey = "web:${d.websites.lastIndex}"
            })
            if (!fullName) {
                add(Opt(Icons.Rounded.Badge, tr("Pseudonim")) {
                    fullName = true
                    show(Sec.NAME)
                    focusKey = "nickname"
                })
                add(Opt(Icons.Rounded.RecordVoiceOver, tr("Wymowa imienia i nazwiska")) {
                    fullName = true
                    show(Sec.NAME)
                    focusKey = "phonetic"
                })
            }
            if (Sec.GROUPS !in shown) add(Opt(Icons.Rounded.Group, tr("Grupy")) { show(Sec.GROUPS) })
            if (Sec.NOTE !in shown) add(Opt(Icons.AutoMirrored.Rounded.Notes, tr("Notatka")) {
                show(Sec.NOTE)
                focusKey = "note"
            })
        }
        ChoiceDialog(tr("Dodaj pole"), options.map { it.icon to it.label }, onDismiss = { addField = false }) { i ->
            addField = false
            options[i].run()
        }
    }

    typeFor?.let { (kind, index) ->
        val close = { typeFor = null }
        when (kind) {
            Kind.PHONE -> TypePicker(Icons.Rounded.Call, Format.phoneTypes, { Format.phoneType(it, null) }, currentLabel = d.phones.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.phones.getOrNull(index)?.let { d = d.copy(phones = d.phones.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.EMAIL -> TypePicker(Icons.Rounded.Email, Format.emailTypes, { Format.emailType(it, null) }, currentLabel = d.emails.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.emails.getOrNull(index)?.let { d = d.copy(emails = d.emails.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.ADDRESS -> TypePicker(Icons.Rounded.Place, ContactKinds.postalTypes, { Format.postalType(it, null) }, currentLabel = d.addresses.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.addresses.getOrNull(index)?.let { d = d.copy(addresses = d.addresses.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.WEB -> TypePicker(Icons.Rounded.Language, ContactKinds.websiteTypes, { Format.websiteType(it, null) }, currentLabel = d.websites.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.websites.getOrNull(index)?.let { d = d.copy(websites = d.websites.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.EVENT -> TypePicker(Icons.Rounded.Event, ContactKinds.eventTypes, { Format.eventType(it, null) }, currentLabel = d.events.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.events.getOrNull(index)?.let { d = d.copy(events = d.events.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.RELATION -> TypePicker(Icons.Rounded.People, ContactKinds.relationTypes, { Format.relationType(it, null) }, currentLabel = d.relations.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.relations.getOrNull(index)?.let { d = d.copy(relations = d.relations.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.SIP -> TypePicker(Icons.Rounded.Dialpad, ContactKinds.sipTypes, { Format.sipType(it, null) }, currentLabel = d.sip.getOrNull(index)?.label, onDismiss = close) { t, l ->
                close()
                d.sip.getOrNull(index)?.let { d = d.copy(sip = d.sip.replaced(index, it.copy(type = t, label = l))) }
            }
            Kind.IM -> ImPicker(onDismiss = close) { t, l ->
                close()
                d.ims.getOrNull(index)?.let { d = d.copy(ims = d.ims.replaced(index, it.copy(type = t, label = l))) }
            }
        }
    }

    if (imPickNew) {
        ImPicker(onDismiss = { imPickNew = false }) { t, l ->
            imPickNew = false
            show(Sec.IM)
            d = d.copy(ims = d.ims + DraftField("", t, l))
            focusKey = "im:${d.ims.lastIndex}"
        }
    }

    dateFor?.let { i ->
        val current = if (i < 0) d.birthday else d.events.getOrNull(i)?.value.orEmpty()
        EventDateDialog(
            current,
            onDismiss = {
                dateFor = null
                // Nowa data bez wybranego dnia – pusty wpis znika.
                if (i >= 0 && d.events.getOrNull(i)?.value?.isBlank() == true) d = d.copy(events = d.events.without(i))
            },
        ) { value ->
            dateFor = null
            show(Sec.EVENTS)
            d = if (i < 0) d.copy(birthday = value) else d.copy(events = d.events.replaced(i, d.events[i].copy(value = value)))
        }
    }

    relationPick?.let { i ->
        val query = PhoneText.fold(d.relations.getOrNull(i)?.value.orEmpty().trim())
        val list = vm.contacts.filter { it.id != d.id && (query.isEmpty() || it.searchName.contains(query)) }.take(200)
        ChoiceDialog(
            tr("Wybierz z kontaktów"),
            list.map { Icons.Rounded.Person to it.name },
            onDismiss = { relationPick = null },
            subtitle = if (list.isEmpty()) tr("Brak dopasowań") else null,
        ) { k ->
            relationPick = null
            d.relations.getOrNull(i)?.let { d = d.copy(relations = d.relations.replaced(i, it.copy(value = list[k].name))) }
        }
    }

    if (photoMenu) {
        val options = buildList<Opt> {
            if (cameraUri != null) add(Opt(Icons.Rounded.PhotoCamera, tr("Zrób zdjęcie")) { runCatching { camera.launch(cameraUri) }.onFailure { vm.toast(tr("Brak aplikacji aparatu")) } })
            add(Opt(Icons.Rounded.PhotoLibrary, tr("Wybierz z galerii")) { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            if (hasPhoto) {
                add(Opt(Icons.Rounded.Crop, tr("Przytnij ponownie")) {
                    val src = lastSource
                    if (src != null) {
                        cropSource = src
                    } else {
                        val bytes = d.photo
                        val id = d.id
                        when {
                            bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { cropSource = it }
                            // Pełne zdjęcie kontaktu, nie miniatura z listy.
                            id != null && existingPhoto != null -> pickedUri = Uri.withAppendedPath(
                                android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, id),
                                android.provider.ContactsContract.Contacts.Photo.DISPLAY_PHOTO,
                            )
                        }
                    }
                })
                add(Opt(Icons.Rounded.Delete, tr("Usuń zdjęcie")) { d = d.copy(photo = null, photoChanged = true) })
            }
        }
        ChoiceDialog(tr("Zdjęcie kontaktu"), options.map { it.icon to it.label }, onDismiss = { photoMenu = false }) { i ->
            photoMenu = false
            options[i].run()
        }
    }

    if (newGroup) {
        TextInputDialog(tr("Nowa grupa"), tr("Nazwa grupy"), onDismiss = { newGroup = false }) { title ->
            newGroup = false
            val id = vm.contactsRepo.createGroup(d.account, title)
            if (id == null) {
                vm.toast(tr("Nie udało się utworzyć grupy"))
            } else {
                d = d.copy(groups = d.groups + id)
                groupsKey++
            }
        }
    }

    if (pickAccount) {
        val choices = if (isNew) accounts else accounts.filter { !it.sim }
        ChoiceDialog(
            if (isNew) tr("Zapisz w") else tr("Przenieś do konta"),
            choices.map { accountIcon(it) to accountLabel(it) },
            onDismiss = { pickAccount = false },
        ) { i ->
            pickAccount = false
            val a = choices[i]
            // Na kartę idzie cała nazwa w jednym polu i pierwszy numer.
            d = if (a.sim) {
                d.copy(
                    account = a,
                    given = d.displayName, prefix = "", middle = "", family = "", suffix = "",
                    phones = listOf(d.phones.firstOrNull { it.value.isNotBlank() } ?: DraftField("", 2)),
                )
            } else {
                // Grupy należą do konta – po zmianie konta wybór grup zaczyna się od nowa.
                d.copy(account = a, groups = if (a == d.account) d.groups else emptySet())
            }
        }
    }

    if (confirmErrors) {
        DarkDialog(
            tr("Sprawdzić pola?"),
            onDismiss = { confirmErrors = false },
            buttons = {
                PillButton(tr("Popraw"), primary = false) { confirmErrors = false }
                PillButton(tr("Zapisz mimo to")) {
                    confirmErrors = false
                    doSave()
                }
            },
        ) {
            Text(tr("Niektóre numery albo adresy e-mail wyglądają na błędne – są zaznaczone na czerwono."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }

    if (discard) {
        DarkDialog(
            tr("Odrzucić zmiany?"),
            onDismiss = { discard = false },
            buttons = {
                PillButton(tr("Edytuj dalej"), primary = false) { discard = false }
                PillButton(tr("Odrzuć"), danger = true) {
                    discard = false
                    vm.pop()
                }
            },
        ) {
            Text(tr("Zmiany w kontakcie nie zostały zapisane."), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

/** Wybór komunikatora: znane z listy albo własny (nazwa). */
@Composable
private fun ImPicker(onDismiss: () -> Unit, onPick: (Int, String?) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        TextInputDialog(tr("Komunikator"), tr("Nazwa komunikatora"), onDismiss = onDismiss) { onPick(ContactKinds.IM_CUSTOM, it) }
        return
    }
    val choices = ContactKinds.imChoices
    ChoiceDialog(
        tr("Komunikator"),
        choices.map { (p, c) -> Icons.Rounded.Chat to ContactKinds.imName(p, c) } + (Icons.AutoMirrored.Rounded.Label to tr("Inny…")),
        onDismiss = onDismiss,
    ) { i ->
        if (i == choices.size) custom = true else onPick(choices[i].first, choices[i].second)
    }
}

/** Sekcja formularza ze zwijanym nagłówkiem (zwinięta pokazuje skrót zawartości). */
@Composable
private fun EditSection(
    sec: Sec,
    title: String,
    icon: ImageVector,
    collapsed: MutableMap<Sec, Boolean>,
    count: Int? = null,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isCollapsed = collapsed[sec] == true
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Fg.copy(alpha = 0.05f))
            .animateContentSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .reactive(if (isCollapsed) tr("Rozwiń: {0}", title) else tr("Zwiń: {0}", title), pressScale = 0.99f) { collapsed[sec] = !isCollapsed }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val info = if (isCollapsed) summary?.takeIf { it.isNotBlank() } ?: count?.takeIf { it > 0 }?.toString() else null
                if (info != null) Text(info, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1)
            }
            if (!isCollapsed && count != null && count > 1) Text(count.toString(), fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(end = 8.dp))
            Icon(if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, null, tint = Palette.TextDim)
        }
        if (!isCollapsed) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Pole tekstowe formularza: „Dalej” na klawiaturze przechodzi do następnego pola. */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: ImageVector? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    focus: Boolean = false,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus) {
            runCatching { requester.requestFocus() }
            onFocused()
        }
    }
    DarkField(
        value, onChange, label, modifier,
        singleLine = singleLine,
        minLines = minLines,
        keyboard = keyboard,
        leading = icon,
        imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        capitalization = cap,
        focusRequester = requester,
    )
}

/** Pole z rodzajem, gwiazdką domyślności, przesuwaniem i usuwaniem pod spodem. */
@Composable
private fun FieldCard(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    icon: ImageVector,
    type: String,
    onType: () -> Unit,
    onRemove: () -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    transformation: VisualTransformation? = null,
    primary: Boolean? = null,
    onPrimary: () -> Unit = {},
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    error: String? = null,
    focus: Boolean = false,
    onFocused: () -> Unit = {},
    extraAction: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus) {
        if (focus) {
            runCatching { requester.requestFocus() }
            onFocused()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        DarkField(
            value, onValue, label,
            keyboard = keyboard,
            leading = icon,
            imeAction = ImeAction.Next,
            capitalization = cap,
            focusRequester = requester,
            isError = error != null,
            visualTransformation = transformation,
        )
        if (error != null) Text(error, fontSize = 11.sp, color = Palette.Record, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
        below?.invoke()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeChip(type, onType)
            if (primary != null) {
                Spacer(Modifier.width(6.dp))
                SelectChip(tr("Domyślny"), primary, icon = if (primary) Icons.Rounded.Star else Icons.Rounded.StarBorder, onClick = onPrimary)
            }
            Spacer(Modifier.weight(1f))
            extraAction?.invoke()
            if (onUp != null) RoundIcon(Icons.Rounded.KeyboardArrowUp, tr("Przenieś wyżej"), size = 32.dp, onClick = onUp)
            if (onDown != null) RoundIcon(Icons.Rounded.KeyboardArrowDown, tr("Przenieś niżej"), size = 32.dp, onClick = onDown)
            RoundIcon(Icons.Rounded.Close, tr("Usuń"), size = 32.dp, onClick = onRemove)
        }
    }
}

/** Adres w polach: ulica, kod i miasto, region, kraj. */
@Composable
private fun AddressCard(a: PostalField, onChange: (PostalField) -> Unit, onType: () -> Unit, onRemove: () -> Unit, focus: Boolean, onFocused: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Field(a.street, { onChange(a.copy(street = it)) }, tr("Ulica i numer"), Icons.Rounded.Place, cap = KeyboardCapitalization.Words, singleLine = false, focus = focus, onFocused = onFocused)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(a.postcode, { onChange(a.copy(postcode = it)) }, tr("Kod"), modifier = Modifier.weight(0.38f))
            Field(a.city, { onChange(a.copy(city = it)) }, tr("Miejscowość"), cap = KeyboardCapitalization.Words, modifier = Modifier.weight(0.62f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(a.region, { onChange(a.copy(region = it)) }, tr("Województwo"), cap = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
            Field(a.country, { onChange(a.copy(country = it)) }, tr("Kraj"), cap = KeyboardCapitalization.Words, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TypeChip(Format.postalType(a.type, a.label), onType)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Close, tr("Usuń adres"), size = 32.dp, onClick = onRemove)
        }
    }
}

/** Data (urodziny, rocznica): dotknięcie otwiera kalendarz. */
@Composable
private fun DateCard(icon: ImageVector, label: String, value: String, birthday: Boolean, onClick: () -> Unit, onType: (() -> Unit)?, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Fg.copy(alpha = 0.06f))
            .reactive(tr("Zmień datę"), pressScale = 0.98f, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (value.isBlank()) tr("Wybierz datę") else Format.eventDate(value), fontSize = 15.sp)
            val sub = listOfNotNull(label, Format.countdown(value, birthday)).joinToString(" · ")
            Text(sub, fontSize = 12.sp, color = Palette.TextDim)
        }
        if (onType != null) TypeChip(tr("Rodzaj"), onType)
        RoundIcon(Icons.Rounded.Close, tr("Usuń"), size = 32.dp, onClick = onRemove)
    }
}

@Composable
private fun TypeChip(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(tr("Zmień rodzaj: {0}", text), pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Fg.copy(alpha = 0.09f))
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextDim, modifier = Modifier.size(16.dp))
    }
}

/** Chip z zaznaczeniem (grupa, „Domyślny”). */
@Composable
private fun SelectChip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(text, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Palette.Fg else Palette.Fg.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) Palette.Ink else Palette.Fg
        val ic = icon ?: if (selected) Icons.Rounded.Check else null
        if (ic != null) {
            Icon(ic, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.reactive(text, onClick = onClick).padding(4.dp),
    )
}

/** Formularz kontaktu na karcie SIM: nazwa i numer z licznikami miejsca na karcie. */
@Composable
private fun SimFields(vm: AppViewModel, d: ContactDraft, card: com.blacksingularities.opencall.data.SimRepo.Card?, onChange: (ContactDraft) -> Unit) {
    val number = d.phones.firstOrNull()?.value.orEmpty()
    val nameBytes = remember(d.given) { runCatching { vm.contactsRepo.sim.nameBytes(d.given.trim()) }.getOrDefault(0) }
    val digits = remember(number) { vm.contactsRepo.sim.cleanNumber(number).count { it != '+' } }
    com.blacksingularities.opencall.ui.components.Group(tr("Karta SIM")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkField(d.given, { v -> onChange(d.copy(given = v)) }, tr("Nazwa"), leading = Icons.Rounded.Person, imeAction = ImeAction.Next)
            if (card != null && card.nameMaxBytes > 0) {
                Text(
                    tr("Miejsce na nazwę: {0} z {1}", nameBytes, card.nameMaxBytes),
                    fontSize = 11.sp,
                    color = if (nameBytes > card.nameMaxBytes) Palette.Record else Palette.TextDim,
                )
            }
            DarkField(number, { v -> onChange(d.copy(phones = listOf(DraftField(v, d.phones.firstOrNull()?.type ?: 2)))) }, tr("Numer"), keyboard = KeyboardType.Phone, leading = Icons.Rounded.Call)
            if (card != null && card.numberMaxDigits > 0) {
                Text(
                    tr("Cyfry: {0} z {1}", digits, card.numberMaxDigits),
                    fontSize = 11.sp,
                    color = if (digits > card.numberMaxDigits) Palette.Record else Palette.TextDim,
                )
            }
            Text(
                if (card == null) tr("Karta SIM jest niedostępna") else tr("Na karcie zapisują się tylko nazwa i jeden numer. Zajęte: {0} z {1}.", card.used, card.capacity),
                fontSize = 12.sp,
                color = Palette.TextDim,
            )
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(label, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Fg.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Palette.Fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
