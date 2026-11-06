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
internal enum class Sec { NAME, PHONE, EMAIL, ADDRESS, WORK, EVENTS, RELATIONS, IM, SIP, WEB, GROUPS, NOTE, OPTIONS }

// Elementy formularza kontaktu: sekcje, pola z rodzajem, adres, daty, chipy i formularz karty SIM.

/** Wybór komunikatora: znane z listy albo własny (nazwa). */
@Composable
internal fun ImPicker(onDismiss: () -> Unit, onPick: (Int, String?) -> Unit) {
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
internal fun EditSection(
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
internal fun Field(
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
internal fun FieldCard(
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
internal fun AddressCard(a: PostalField, onChange: (PostalField) -> Unit, onType: () -> Unit, onRemove: () -> Unit, focus: Boolean, onFocused: () -> Unit) {
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
internal fun DateCard(icon: ImageVector, label: String, value: String, birthday: Boolean, onClick: () -> Unit, onType: (() -> Unit)?, onRemove: () -> Unit) {
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
internal fun TypeChip(text: String, onClick: () -> Unit) {
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
internal fun SelectChip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
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
internal fun LinkText(text: String, onClick: () -> Unit) {
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
internal fun SimFields(vm: AppViewModel, d: ContactDraft, card: com.blacksingularities.opencall.data.SimRepo.Card?, onChange: (ContactDraft) -> Unit) {
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
internal fun AddRow(label: String, onClick: () -> Unit) {
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
