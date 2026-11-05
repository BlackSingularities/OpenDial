package com.blacksingularities.opencall.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.AccountRef
import com.blacksingularities.opencall.data.ContactDraft
import com.blacksingularities.opencall.data.DraftField
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.Group
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.PhotoCropper
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Formularz kontaktu: nazwa (z rozwinięciem na człony), numery, e-maile, adresy, firma, urodziny, notatka, zdjęcie. */
@Composable
fun EditContactScreen(vm: AppViewModel, initial: ContactDraft) {
    var d by remember(initial) { mutableStateOf(initial) }
    var fullName by remember { mutableStateOf(initial.prefix.isNotBlank() || initial.middle.isNotBlank() || initial.suffix.isNotBlank()) }
    var more by remember { mutableStateOf(initial.organization.isNotBlank() || initial.nickname.isNotBlank()) }
    var typeFor by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var pickAccount by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val accounts = remember { runCatching { vm.contactsRepo.accounts() }.getOrDefault(emptyList()) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pickedUri = uri }
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        cropSource = withContext(Dispatchers.IO) { vm.contactsRepo.loadPhotoForCrop(uri) }
        if (cropSource == null) vm.toast(tr("Nie udało się wczytać zdjęcia"))
        pickedUri = null
    }
    val isNew = d.id == null
    val changed = d != initial
    // Karta SIM mieści tylko nazwę i jeden numer – formularz pokazuje wtedy tylko te pola.
    val onSim = d.account?.sim == true
    val simCard = remember(d.account) { d.account?.simSubId?.let { sub -> runCatching { vm.contactsRepo.sim.cards() }.getOrNull()?.firstOrNull { it.subscriptionId == sub } } }

    fun save() {
        if (d.isEmpty) {
            vm.toast(tr("Wpisz nazwę albo numer"))
            return
        }
        vm.saveContact(d) { id ->
            vm.pop()
            if (isNew && id != null) vm.push(Route.ContactInfo(id))
        }
    }

    androidx.activity.compose.BackHandler(enabled = changed) { discard = true }

    cropSource?.let { src ->
        PhotoCropper(src, onCancel = { cropSource = null }) { bmp, left, top, side ->
            vm.contactsRepo.cropPhoto(bmp, left, top, side)?.let { d = d.copy(photo = it, photoChanged = true) }
            cropSource = null
        }
    }

    Page(
        if (isNew) tr("Nowy kontakt") else tr("Edytuj kontakt"),
        onBack = { if (changed) discard = true else vm.pop() },
        actions = { PillButton(if (vm.simSaving) tr("Zapisywanie…") else tr("Zapisz"), icon = Icons.Rounded.Check, enabled = !vm.simSaving, onClick = ::save) },
    ) {
        if (!onSim) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .reactive(tr("Zmień zdjęcie")) { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    .size(104.dp),
            ) {
                val photo = d.photo
                val bmp = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                if (bmp != null) {
                    Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Avatar(d.displayName, if (isNew) null else vm.contacts.firstOrNull { it.id == d.id }?.photoUri, 104.dp)
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(Palette.Fg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
                }
            }
            if (d.photo != null) {
                Text(
                    tr("Usuń zdjęcie"),
                    color = Palette.Record,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp).reactive(tr("Usuń zdjęcie")) { d = d.copy(photo = null, photoChanged = true) },
                )
            }
        }

        if (isNew && accounts.size > 1) {
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
                    Text(tr("Zapisz w"), fontSize = 11.sp, color = Palette.TextDim)
                    Text(accountLabel(acc), fontSize = 14.sp)
                }
                Icon(Icons.Rounded.ExpandMore, null, tint = Palette.TextDim)
            }
        }

        if (onSim) {
            SimFields(vm, d, simCard) { d = it }
        }

        if (!onSim) Group(tr("Nazwa")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (fullName) DarkField(d.prefix, { v -> d = d.copy(prefix = v) }, tr("Tytuł (np. dr)"))
                DarkField(d.given, { v -> d = d.copy(given = v) }, tr("Imię"), leading = Icons.Rounded.Person)
                if (fullName) DarkField(d.middle, { v -> d = d.copy(middle = v) }, tr("Drugie imię"))
                DarkField(d.family, { v -> d = d.copy(family = v) }, tr("Nazwisko"))
                if (fullName) DarkField(d.suffix, { v -> d = d.copy(suffix = v) }, tr("Przyrostek"))
                if (!fullName) {
                    Text(tr("Więcej pól nazwy"), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(4.dp).reactive(tr("Więcej pól nazwy")) { fullName = true })
                }
            }
        }

        if (!onSim) Group(tr("Telefon")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.phones.forEachIndexed { i, p ->
                    FieldWithType(
                        value = p.value,
                        type = Format.phoneType(p.type, p.label),
                        label = tr("Numer"),
                        icon = Icons.Rounded.Call,
                        keyboard = KeyboardType.Phone,
                        onChange = { v -> d = d.copy(phones = d.phones.toMutableList().also { it[i] = p.copy(value = v) }) },
                        onType = { typeFor = true to i },
                        onRemove = { d = d.copy(phones = d.phones.toMutableList().also { it.removeAt(i) }) },
                    )
                }
                AddRow(tr("Dodaj numer")) { d = d.copy(phones = d.phones + DraftField("", if (d.phones.isEmpty()) 2 else 1)) }
            }
        }

        if (!onSim) Group(tr("E-mail")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.emails.forEachIndexed { i, e ->
                    FieldWithType(
                        value = e.value,
                        type = Format.emailType(e.type, e.label),
                        label = tr("Adres e-mail"),
                        icon = Icons.Rounded.Email,
                        keyboard = KeyboardType.Email,
                        onChange = { v -> d = d.copy(emails = d.emails.toMutableList().also { it[i] = e.copy(value = v) }) },
                        onType = { typeFor = false to i },
                        onRemove = { d = d.copy(emails = d.emails.toMutableList().also { it.removeAt(i) }) },
                    )
                }
                AddRow(tr("Dodaj e-mail")) { d = d.copy(emails = d.emails + DraftField("", 1)) }
            }
        }

        if (!onSim) Group(tr("Adres")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.addresses.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DarkField(a.value, { v -> d = d.copy(addresses = d.addresses.toMutableList().also { it[i] = a.copy(value = v) }) }, tr("Adres"), Modifier.weight(1f), singleLine = false, minLines = 2, leading = Icons.Rounded.Place)
                        RoundIcon(Icons.Rounded.Close, tr("Usuń"), size = 36.dp) { d = d.copy(addresses = d.addresses.toMutableList().also { it.removeAt(i) }) }
                    }
                }
                AddRow(tr("Dodaj adres")) { d = d.copy(addresses = d.addresses + DraftField("", 1)) }
            }
        }

        if (!onSim) Group(tr("Więcej")) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DarkField(d.organization, { v -> d = d.copy(organization = v) }, tr("Firma"), leading = Icons.Rounded.Business)
                DarkField(d.title, { v -> d = d.copy(title = v) }, tr("Stanowisko"))
                if (more) DarkField(d.nickname, { v -> d = d.copy(nickname = v) }, tr("Pseudonim"))
                DarkField(d.birthday, { v -> d = d.copy(birthday = v.filter { it.isDigit() || it == '-' }.take(10)) }, tr("Urodziny (RRRR-MM-DD)"), keyboard = KeyboardType.Number, leading = Icons.Rounded.Cake)
                d.websites.forEachIndexed { i, w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DarkField(w, { v -> d = d.copy(websites = d.websites.toMutableList().also { it[i] = v }) }, tr("Strona WWW"), Modifier.weight(1f), keyboard = KeyboardType.Uri, leading = Icons.Rounded.Language)
                        RoundIcon(Icons.Rounded.Close, tr("Usuń"), size = 36.dp) { d = d.copy(websites = d.websites.toMutableList().also { it.removeAt(i) }) }
                    }
                }
                DarkField(d.note, { v -> d = d.copy(note = v) }, tr("Notatka"), singleLine = false, minLines = 3, leading = Icons.AutoMirrored.Rounded.Notes)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddRow(tr("Strona WWW")) { d = d.copy(websites = d.websites + "") }
                    if (!more) AddRow(tr("Pseudonim")) { more = true }
                }
            }
        }
        Spacer(Modifier.size(24.dp))
    }

    typeFor?.let { (phone, index) ->
        val types = if (phone) Format.phoneTypes else Format.emailTypes
        ChoiceDialog(
            tr("Rodzaj"),
            types.map { (if (phone) Icons.Rounded.Call else Icons.Rounded.Email) to if (phone) Format.phoneType(it, null) else Format.emailType(it, null) },
            onDismiss = { typeFor = null },
        ) { i ->
            typeFor = null
            if (phone) {
                d = d.copy(phones = d.phones.toMutableList().also { it[index] = it[index].copy(type = types[i], label = null) })
            } else {
                d = d.copy(emails = d.emails.toMutableList().also { it[index] = it[index].copy(type = types[i], label = null) })
            }
        }
    }
    if (pickAccount) {
        ChoiceDialog(
            tr("Zapisz w"),
            accounts.map { accountIcon(it) to accountLabel(it) },
            onDismiss = { pickAccount = false },
        ) { i ->
            pickAccount = false
            val a = accounts[i]
            // Na kartę idzie cała nazwa w jednym polu i pierwszy numer.
            d = if (a.sim) {
                d.copy(
                    account = a,
                    given = d.displayName, prefix = "", middle = "", family = "", suffix = "",
                    phones = listOf(d.phones.firstOrNull { it.value.isNotBlank() } ?: DraftField("", 2)),
                )
            } else {
                d.copy(account = a)
            }
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

private fun accountIcon(a: AccountRef?): ImageVector = when {
    a == null || a.local -> Icons.Rounded.Smartphone
    a.sim -> Icons.Rounded.SimCard
    else -> Icons.Rounded.Cloud
}

/** Formularz kontaktu na karcie SIM: nazwa i numer z licznikami miejsca na karcie. */
@Composable
private fun SimFields(vm: AppViewModel, d: ContactDraft, card: com.blacksingularities.opencall.data.SimRepo.Card?, onChange: (ContactDraft) -> Unit) {
    val number = d.phones.firstOrNull()?.value.orEmpty()
    val nameBytes = remember(d.given) { runCatching { vm.contactsRepo.sim.nameBytes(d.given.trim()) }.getOrDefault(0) }
    val digits = remember(number) { vm.contactsRepo.sim.cleanNumber(number).count { it != '+' } }
    Group(tr("Karta SIM")) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DarkField(d.given, { v -> onChange(d.copy(given = v)) }, tr("Nazwa"), leading = Icons.Rounded.Person)
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

private fun accountLabel(a: AccountRef?): String = when {
    a == null || a.local -> tr("Telefon (bez synchronizacji)")
    a.sim -> tr("Karta SIM {0}", a.simSlot + 1)
    a.type?.contains("google") == true -> "Google · ${a.name}"
    else -> a.name ?: ""
}

@Composable
private fun FieldWithType(
    value: String,
    type: String,
    label: String,
    icon: ImageVector,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
    onType: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DarkField(value, onChange, label, Modifier.weight(1f), keyboard = keyboard, leading = icon)
        Spacer(Modifier.width(6.dp))
        Text(
            type,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .reactive(tr("Zmień rodzaj"), onClick = onType)
                .clip(RoundedCornerShape(50))
                .background(Palette.Fg.copy(alpha = 0.09f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
        RoundIcon(Icons.Rounded.Close, tr("Usuń"), size = 34.dp, onClick = onRemove)
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
