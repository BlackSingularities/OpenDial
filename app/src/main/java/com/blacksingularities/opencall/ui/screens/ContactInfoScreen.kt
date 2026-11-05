package com.blacksingularities.opencall.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.rounded.Work
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.ContactDetails
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wybór dzwonka kontaktu (systemowy selektor dzwonków). */
private class PickRingtone : ActivityResultContract<String?, Uri?>() {
    override fun createIntent(context: android.content.Context, input: String?) = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input?.let { Uri.parse(it) })

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != android.app.Activity.RESULT_OK) return null
        @Suppress("DEPRECATION")
        return intent?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: Uri.EMPTY
    }
}

/**
 * Szczegóły kontaktu ([contactId]) albo samego numeru spoza kontaktów ([number]): akcje,
 * numery, e-maile, adresy, historia połączeń, blokowanie, dzwonek i poczta głosowa.
 */
@Composable
fun ContactInfoScreen(vm: AppViewModel, contactId: Long?, number: String?) {
    var details by remember(contactId) { mutableStateOf<ContactDetails?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(contactId, reloadKey, vm.contacts) {
        if (contactId != null) details = withContext(Dispatchers.IO) { vm.contactsRepo.details(contactId) }
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
    var pickNumber by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf(false) }
    var numberMenu by remember { mutableStateOf<String?>(null) }
    val ringtone = rememberLauncherForActivityResult(PickRingtone()) { uri ->
        if (uri != null && d != null) {
            vm.contactsRepo.setRingtone(d.id, uri.takeIf { it != Uri.EMPTY }?.toString())
            reloadKey++
            vm.toast(tr("Dzwonek ustawiony"))
        }
    }

    /** Akcja na numerze; przy kilku numerach – najpierw wybór. */
    fun withNumber(f: (String) -> Unit) {
        when (numbers.size) {
            0 -> Unit
            1 -> f(numbers[0])
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
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(d?.name, d?.photoUri, 112.dp)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            val sub = listOfNotNull(d?.nickname?.let { "„$it”" }, listOfNotNull(d?.title, d?.organization).joinToString(", ").ifBlank { null })
            if (sub.isNotEmpty()) Text(sub.joinToString(" · "), fontSize = 14.sp, color = Palette.TextDim, textAlign = TextAlign.Center)
            if (d == null) {
                val loc = history.firstNotNullOfOrNull { it.location }
                if (loc != null) Text(loc, fontSize = 14.sp, color = Palette.TextDim)
            }
            if (numbers.any { vm.isBlocked(it) }) {
                Text(tr("Zablokowany"), color = Palette.Record, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigAction(Icons.Rounded.Call, tr("Zadzwoń"), enabled = numbers.isNotEmpty()) { withNumber { vm.call(it, d?.name) } }
                BigAction(Icons.AutoMirrored.Rounded.Message, tr("SMS"), enabled = numbers.isNotEmpty()) { withNumber { vm.sms(it) } }
                if (d != null && d.emails.isNotEmpty()) {
                    BigAction(Icons.Rounded.Email, tr("E-mail")) { vm.email(d.emails.first().value) }
                }
                BigAction(Icons.Rounded.Share, tr("Udostępnij")) {
                    if (d != null) vm.shareVCard(d.id) else vm.shareText(number ?: "")
                }
            }
        }

        if (numbers.isNotEmpty()) {
            Group(tr("Numery")) {
                if (d != null) {
                    d.phones.forEach { p ->
                        NumberRow(vm, p.number, Format.phoneType(p.type, p.label) + if (p.primary) " · " + tr("domyślny") else "", onLong = { numberMenu = p.number })
                    }
                } else {
                    NumberRow(vm, number!!, history.firstNotNullOfOrNull { it.location } ?: tr("Numer"), onLong = { numberMenu = number })
                }
            }
        }
        if (d != null) {
            if (d.emails.isNotEmpty()) {
                Group(tr("E-mail")) {
                    d.emails.forEach { e ->
                        ActionRow(Icons.Rounded.Email, e.value, Format.emailType(e.type, e.label), onLongClick = { vm.copy(e.value) }) { vm.email(e.value) }
                    }
                }
            }
            if (d.addresses.isNotEmpty()) {
                Group(tr("Adres")) {
                    d.addresses.forEach { a -> ActionRow(Icons.Rounded.Place, a.value, tr("Pokaż na mapie"), onLongClick = { vm.copy(a.value) }) { vm.map(a.value) } }
                }
            }
            val info = buildList<Triple<ImageVector, String, String>> {
                d.birthday?.let { add(Triple(Icons.Rounded.Cake, tr("Urodziny"), Format.birthday(it))) }
                d.organization?.let { add(Triple(Icons.Rounded.Work, tr("Firma"), listOfNotNull(it, d.title).joinToString(" · "))) }
                d.websites.forEach { add(Triple(Icons.Rounded.Language, tr("Strona"), it)) }
                d.note?.let { add(Triple(Icons.AutoMirrored.Rounded.Notes, tr("Notatka"), it)) }
            }
            if (info.isNotEmpty()) {
                Group(tr("Informacje")) {
                    info.forEach { (icon, label, value) ->
                        ActionRow(icon, value, label, onLongClick = { vm.copy(value) }) { if (icon == Icons.Rounded.Language) vm.web(value) else vm.copy(value) }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            Group(tr("Historia połączeń · {0}", history.size)) {
                history.take(15).forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        val missed = e.kind == CallKind.MISSED || e.kind == CallKind.REJECTED
                        Icon(kindIcon(e.kind), null, tint = if (missed) Palette.Record else Palette.TextDim, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Format.kind(e.kind), fontSize = 14.sp, color = if (missed) Palette.Record else Palette.Fg)
                            Text(Format.full(e.date) + if (numbers.size > 1) " · " + PhoneText.pretty(e.number) else "", fontSize = 11.sp, color = Palette.TextDim)
                        }
                        if (e.durationSec > 0) Text(PhoneText.duration(e.durationSec), fontSize = 13.sp, color = Palette.TextDim)
                    }
                }
                if (history.size > 15) Text(tr("…i {0} wcześniejszych", history.size - 15), fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(12.dp))
                val talk = history.sumOf { it.durationSec }
                if (talk > 0) {
                    Text(
                        tr("Łącznie rozmów: {0}", PhoneText.durationShort(talk, tr("h"), tr("min"), tr("s"))),
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }

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
        ChoiceDialog(
            PhoneText.pretty(n),
            buildList {
                add(Icons.Rounded.ContentCopy to tr("Kopiuj numer"))
                add(Icons.Rounded.Call to tr("Edytuj przed połączeniem"))
                if (d != null && d.phones.size > 1) add(Icons.Rounded.Star to tr("Ustaw jako domyślny"))
            },
            onDismiss = { numberMenu = null },
        ) { i ->
            numberMenu = null
            when (i) {
                0 -> vm.copy(n)
                1 -> {
                    vm.dialed = PhoneText.digits(n)
                    vm.stack.clear()
                    vm.openKeypad()
                }
                2 -> if (d != null) {
                    vm.contactsRepo.setPrimaryNumber(d.id, n)
                    reloadKey++
                }
            }
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

private fun ringtoneName(vm: AppViewModel, uri: String): String = runCatching {
    RingtoneManager.getRingtone(vm.getApplication(), Uri.parse(uri))?.getTitle(vm.getApplication())
}.getOrNull() ?: tr("Własny")

@Composable
private fun NumberRow(vm: AppViewModel, number: String, label: String, onLong: () -> Unit) {
    ActionRow(
        Icons.Rounded.Call,
        PhoneText.pretty(number),
        label,
        onLongClick = onLong,
        trailing = {
            RoundIcon(Icons.AutoMirrored.Rounded.Message, tr("SMS"), size = 36.dp) { vm.sms(number) }
        },
    ) { vm.call(number) }
}

@Composable
private fun BigAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .reactive(label, enabled, onClick = onClick)
            .width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Fg.copy(alpha = 0.09f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Palette.Fg, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Palette.TextDim)
    }
}
