package com.blacksingularities.opencall.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.AccountRef
import com.blacksingularities.opencall.data.ContactKinds
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Calendar
import java.util.TimeZone

/** Wybór dzwonka kontaktu (systemowy selektor dzwonków). Wynik Uri.EMPTY = dzwonek domyślny. */
internal class PickRingtone : ActivityResultContract<String?, Uri?>() {
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

internal fun ringtoneName(vm: AppViewModel, uri: String): String = runCatching {
    RingtoneManager.getRingtone(vm.getApplication(), Uri.parse(uri))?.getTitle(vm.getApplication())
}.getOrNull() ?: tr("Własny")

internal fun accountIcon(a: AccountRef?): ImageVector = when {
    a == null || a.local -> Icons.Rounded.Smartphone
    a.sim -> Icons.Rounded.SimCard
    else -> Icons.Rounded.Cloud
}

internal fun accountLabel(a: AccountRef?): String = when {
    a == null || a.local -> tr("Telefon (bez synchronizacji)")
    a.sim -> tr("Karta SIM {0}", a.simSlot + 1)
    a.type?.contains("google") == true -> "Google · ${a.name}"
    else -> a.name ?: ""
}

/** Krótka nazwa konta na plakietkę: „Google”, „Telefon”, „SIM 1”, „WhatsApp”. */
internal fun accountBadge(a: AccountRef): String {
    val type = a.type.orEmpty().lowercase()
    return when {
        a.sim -> tr("SIM {0}", a.simSlot + 1)
        a.local -> tr("Telefon")
        "google" in type -> "Google"
        "whatsapp" in type -> "WhatsApp"
        "telegram" in type -> "Telegram"
        "signal" in type -> "Signal"
        "exchange" in type || "outlook" in type || "microsoft" in type -> "Exchange"
        "carddav" in type || "davx" in type || "bitfire" in type -> "CardDAV"
        else -> a.name ?: a.type ?: tr("Konto")
    }
}

/** Okno z polem tekstowym (własna etykieta, nazwa nowej grupy). */
@Composable
internal fun TextInputDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DarkDialog(
        title,
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("OK"), enabled = text.isNotBlank()) { onOk(text.trim()) }
        },
    ) {
        DarkField(text, { text = it }, label, imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences, focusRequester = focus)
    }
}

/**
 * Wybór rodzaju pola z listy [types] (etykiety z [labelOf]) i pozycją „Własny…”, która pyta o
 * własną etykietę. [onPick] dostaje (rodzaj, etykieta) – etykieta tylko dla rodzaju CUSTOM.
 */
@Composable
internal fun TypePicker(
    icon: ImageVector,
    types: List<Int>,
    labelOf: (Int) -> String,
    customType: Int = ContactKinds.CUSTOM,
    currentLabel: String? = null,
    onDismiss: () -> Unit,
    onPick: (Int, String?) -> Unit,
) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        TextInputDialog(tr("Własna etykieta"), tr("Etykieta"), currentLabel.orEmpty(), onDismiss = onDismiss) { onPick(customType, it) }
        return
    }
    ChoiceDialog(
        tr("Rodzaj"),
        types.map { icon to labelOf(it) } + (Icons.Rounded.Edit to tr("Własny…")),
        onDismiss = onDismiss,
    ) { i ->
        if (i == types.size) custom = true else onPick(types[i], null)
    }
}

/** Menu długiego przytrzymania pola kontaktu: kopiuj, udostępnij, otwórz w… */
@Composable
internal fun FieldMenu(vm: AppViewModel, value: String, onDismiss: () -> Unit) {
    ChoiceDialog(
        value.lines().first().take(60),
        listOf(
            Icons.Rounded.ContentCopy to tr("Kopiuj"),
            Icons.Rounded.Share to tr("Udostępnij"),
            Icons.AutoMirrored.Rounded.OpenInNew to tr("Otwórz w…"),
        ),
        onDismiss = onDismiss,
    ) { i ->
        onDismiss()
        when (i) {
            0 -> vm.copy(value)
            1 -> vm.shareText(value)
            2 -> vm.openWith(value)
        }
    }
}

/**
 * Kalendarz do wyboru daty wydarzenia z opcją „bez roku” (zapis „--MM-DD”, jak w systemowych
 * kontaktach). [initial] w zapisie kontaktu; [onPick] dostaje gotowy zapis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDateDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val parsed = remember(initial) { ContactKinds.parseDate(initial) }
    var noYear by remember { mutableStateOf(parsed != null && parsed.first == null) }
    val utc = TimeZone.getTimeZone("UTC")
    val startMillis = remember(parsed) {
        parsed?.let { (y, m, d) ->
            Calendar.getInstance(utc).apply {
                clear()
                // Bez roku: rok przestępny, żeby dało się wybrać 29 lutego.
                set(y ?: 2000, m - 1, d)
            }.timeInMillis
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = startMillis, yearRange = 1900..2100)
    val colors = DatePickerDefaults.colors(
        containerColor = Palette.PanelHigh,
        titleContentColor = Palette.TextDim,
        headlineContentColor = Palette.Fg,
        weekdayContentColor = Palette.TextDim,
        subheadContentColor = Palette.TextDim,
        navigationContentColor = Palette.Fg,
        yearContentColor = Palette.Fg,
        currentYearContentColor = Palette.Fg,
        selectedYearContentColor = Palette.Ink,
        selectedYearContainerColor = Palette.Fg,
        dayContentColor = Palette.Fg,
        selectedDayContentColor = Palette.Ink,
        selectedDayContainerColor = Palette.Fg,
        todayContentColor = Palette.Fg,
        todayDateBorderColor = Palette.Fg,
        dividerColor = Palette.Fg.copy(alpha = 0.15f),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            PillButton(tr("OK"), enabled = state.selectedDateMillis != null, modifier = Modifier.padding(end = 12.dp, bottom = 8.dp)) {
                val millis = state.selectedDateMillis ?: return@PillButton
                val c = Calendar.getInstance(utc).apply { timeInMillis = millis }
                onPick(ContactKinds.formatDate(if (noYear) null else c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)))
            }
        },
        dismissButton = {
            PillButton(tr("Anuluj"), primary = false, modifier = Modifier.padding(end = 8.dp, bottom = 8.dp), onClick = onDismiss)
        },
    ) {
        DatePicker(state, colors = colors, showModeToggle = true)
        Row(
            Modifier
                .fillMaxWidth()
                .reactive(tr("Bez roku"), pressScale = 0.98f) { noYear = !noYear }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = noYear,
                onCheckedChange = { noYear = it },
                colors = CheckboxDefaults.colors(checkedColor = Palette.Fg, checkmarkColor = Palette.Ink, uncheckedColor = Palette.TextDim),
            )
            Text(tr("Bez roku"), fontSize = 14.sp, color = Palette.Fg)
        }
    }
}

/**
 * Formatowanie numeru w trakcie pisania: wyświetla bloki „+48 600 123 456”, a w polu zostają
 * same znaki wpisane przez użytkownika. Gdy formater zmienia coś poza spacjami – bez zmian.
 */
internal object PhoneFormatTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty() || raw.any { it.isWhitespace() }) return TransformedText(text, OffsetMapping.Identity)
        val pretty = PhoneText.pretty(raw)
        if (pretty == raw || pretty.filter { !it.isWhitespace() } != raw) return TransformedText(text, OffsetMapping.Identity)
        // Pozycja każdego znaku oryginału w tekście sformatowanym.
        val toTransformed = IntArray(raw.length + 1)
        var j = 0
        pretty.forEachIndexed { k, ch ->
            if (!ch.isWhitespace()) {
                toTransformed[j] = k
                j++
            }
        }
        toTransformed[raw.length] = pretty.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toTransformed[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int =
                pretty.take(offset.coerceIn(0, pretty.length)).count { !it.isWhitespace() }
        }
        return TransformedText(AnnotatedString(pretty), mapping)
    }
}

/** Kod QR z wizytówką vCard – do zeskanowania aparatem drugiego telefonu. */
@Composable
internal fun QrDialog(title: String, text: String, onDismiss: () -> Unit) {
    val matrix = remember(text) { qrMatrix(text) }
    DarkDialog(title, onDismiss = onDismiss, buttons = { PillButton(tr("Zamknij"), onClick = onDismiss) }) {
        if (matrix == null) {
            Text(tr("Za dużo danych na kod QR – udostępnij kontakt jako plik."), color = Palette.TextDim, fontSize = 13.sp)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(14.dp),
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val n = matrix.width
                    val cell = size.width / n
                    for (y in 0 until n) {
                        for (x in 0 until n) {
                            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                        }
                    }
                }
            }
            Text(tr("Zeskanuj aparatem w drugim telefonie, aby dodać kontakt."), color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}

private fun qrMatrix(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        ),
    )
}.getOrNull()

/** Plakietka: kropka i krótki tekst na delikatnym tle. */
@Composable
internal fun Badge(text: String, color: Color = Palette.Fg, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        } else {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        }
        Text(text, fontSize = 12.sp, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Odstęp poziomy – skrót do wierszy z chipami. */
@Composable
internal fun HGap(w: Int = 6) = Spacer(Modifier.width(w.dp))
