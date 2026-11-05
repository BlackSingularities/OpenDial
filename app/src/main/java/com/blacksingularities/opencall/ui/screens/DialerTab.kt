package com.blacksingularities.opencall.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Keypad
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

private data class Suggestion(val contact: Contact?, val number: String, val score: Int)

/** Podpowiedzi dla wpisanych cyfr: dopasowanie T9 nazwy albo fragmentu numeru, potem numery z historii. */
@Composable
private fun rememberSuggestions(vm: AppViewModel, digits: String): List<Suggestion> =
    remember(digits, vm.contacts, vm.calls, vm.settings.t9Search) {
        val q = digits.filter { it.isDigit() }
        if (q.isEmpty()) return@remember emptyList()
        val out = ArrayList<Suggestion>()
        vm.contacts.forEach { c ->
            val t9 = if (vm.settings.t9Search) PhoneText.t9Score(c.t9, q) else 0
            val byNumber = c.numbers.firstOrNull { PhoneText.numberMatches(it.number, q) }
            when {
                byNumber != null -> out.add(Suggestion(c, byNumber.number, 4))
                t9 > 0 && c.primaryNumber != null -> out.add(Suggestion(c, c.primaryNumber!!.number, t9))
            }
        }
        vm.calls.asSequence()
            .filter { !it.hidden && PhoneText.numberMatches(it.number, q) }
            .distinctBy { PhoneText.key(it.number) }
            .filter { e -> out.none { PhoneText.sameNumber(it.number, e.number) } }
            .take(5)
            .forEach { out.add(Suggestion(null, it.number, 1)) }
        out.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.contact?.name ?: "" }).take(30)
    }

/** Lista kontaktów i numerów pasujących do cyfr z klawiatury – zastępuje historię, gdy coś wpisano. */
@Composable
fun DialSuggestions(vm: AppViewModel, modifier: Modifier = Modifier) {
    val digits = vm.dialed
    val suggestions = rememberSuggestions(vm, digits)
    val q = digits.filter { it.isDigit() }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
        if (suggestions.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, tr("Brak dopasowań"), tr("Naciśnij zieloną słuchawkę, aby zadzwonić pod ten numer.")) }
        }
        items(suggestions, key = { (it.contact?.id?.toString() ?: "n") + it.number }) { s ->
            Row(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .reactive(null, pressScale = 0.98f) {
                        vm.call(s.number, s.contact?.name)
                        vm.dialed = ""
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(s.contact?.name, s.contact?.photoUri, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.contact?.name?.let { androidx.compose.ui.text.AnnotatedString(it) } ?: highlight(PhoneText.pretty(s.number), q), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val type = s.contact?.numbers?.firstOrNull { it.number == s.number }?.let { Format.phoneType(it.type, it.label) }
                    Text(listOfNotNull(type, if (s.contact != null) PhoneText.pretty(s.number) else tr("Z historii")).joinToString(" · "), fontSize = 12.sp, color = Palette.TextDim, maxLines = 1)
                }
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Palette.Answer.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Call, null, tint = Palette.Answer, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Pogrubia w numerze cyfry wpisane na klawiaturze (pierwsze dopasowanie). */
private fun highlight(pretty: String, q: String) = buildAnnotatedString {
    val digitsOnly = pretty.filter { it.isDigit() }
    val start = if (q.isEmpty()) -1 else digitsOnly.indexOf(q)
    var seen = 0
    pretty.forEach { ch ->
        val inside = ch.isDigit() && start >= 0 && seen >= start && seen < start + q.length
        if (ch.isDigit()) seen++
        if (inside) withStyle(SpanStyle(color = Palette.Answer, fontWeight = FontWeight.Bold)) { append(ch) } else append(ch)
    }
}

/**
 * Wysuwana klawiatura zakładki „Telefon”: wpisany numer, akcje dla obcego numeru, klawisze
 * i rząd z przyciskiem połączenia – jak w telefonach Samsunga czy Huaweia.
 */
@Composable
fun DialerPanel(vm: AppViewModel, onCollapse: () -> Unit) {
    val tones = remember {
        runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()
    }
    DisposableEffect(Unit) { onDispose { tones?.release() } }
    val digits = vm.dialed

    fun press(k: Char) {
        if (vm.settings.dtmfTones) {
            val tone = when (k) {
                in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (k - '0')
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> null
            }
            tone?.let { tones?.startTone(it, 120) }
        }
        vm.dialed = vm.dialed + k
        // Kody serwisowe działają od razu po wpisaniu ostatniego znaku, bez przycisku połączenia.
        if (k == '#' || k == '*') vm.instantCode()
    }

    // Czarne tło jak dolny pasek – klawiatura przechodzi w niego bez szwu.
    // Przeciągnięcie w dół zwija klawiaturę.
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 60.dp.toPx()) onCollapse() },
                ) { _, dy -> drag += dy }
            }
            .background(Palette.Ink)
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Uchwyt – dotknięcie zwija klawiaturę.
        Box(
            Modifier
                .reactive(tr("Zwiń klawiaturę"), pressScale = 0.9f, onClick = onCollapse)
                .padding(horizontal = 30.dp, vertical = 4.dp),
        ) {
            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Palette.TextFaint))
        }
        if (vm.addingCall) {
            Text(tr("Dodaj połączenie"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Answer, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (digits.isEmpty()) {
                RoundIcon(Icons.Rounded.ContentPaste, tr("Wklej numer"), tint = Palette.TextDim) { vm.pasteNumber()?.let { vm.dialed = it } }
            } else {
                Spacer(Modifier.size(44.dp))
            }
            Text(
                if (digits.isEmpty()) tr("Wpisz numer") else PhoneText.pretty(digits).takeIf { digits.length in 9..12 } ?: digits,
                fontSize = when {
                    digits.isEmpty() -> 18.sp
                    digits.length > 16 -> 24.sp
                    digits.length > 12 -> 30.sp
                    else -> 36.sp
                },
                color = if (digits.isEmpty()) Palette.TextFaint else Palette.Fg,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            Spacer(Modifier.size(44.dp))
        }
        val known = digits.length >= 3 && vm.contactFor(digits) != null
        AnimatedVisibility(digits.length >= 3 && !known, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                SmallPill(Icons.Rounded.PersonAdd, tr("Dodaj do kontaktów")) { vm.newContact(digits) }
                SmallPill(Icons.AutoMirrored.Rounded.Message, tr("SMS")) { vm.sms(digits) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val key = (maxWidth / 4.6f).coerceAtMost(72.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Keypad(
                    haptics = vm.settings.keypadVibrate,
                    keySize = key,
                    speedDial = vm.settings.speedDial.keys,
                    onKey = { press(it) },
                    onLongKey = { k ->
                        when (k) {
                            '0' -> {
                                vm.dialed = vm.dialed + "+"
                                true
                            }
                            '1' -> if (vm.dialed.isEmpty()) {
                                vm.voicemail()
                                true
                            } else {
                                false
                            }
                            in '2'..'9' -> vm.dialed.isEmpty() && vm.speedDial(k - '0')
                            else -> false
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(key), contentAlignment = Alignment.Center) {
                        RoundIcon(Icons.Rounded.KeyboardArrowDown, tr("Zwiń klawiaturę"), tint = Palette.TextDim, onClick = onCollapse)
                    }
                    Box(
                        Modifier
                            .reactive(tr("Zadzwoń")) {
                                if (digits.isEmpty()) {
                                    // Puste pole: ostatni wybierany numer, jak w klasycznych telefonach.
                                    vm.calls.firstOrNull { it.kind == CallKind.OUTGOING }?.let { vm.dialed = PhoneText.digits(it.number) }
                                } else {
                                    vm.call(digits, vm.contactFor(digits)?.name)
                                    vm.dialed = ""
                                }
                            }
                            .size(width = key * 1.25f, height = key * 0.9f)
                            .clip(RoundedCornerShape(50))
                            .background(Palette.Answer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Box(Modifier.size(key), contentAlignment = Alignment.Center) {
                        if (digits.isNotEmpty()) {
                            RoundIcon(
                                Icons.AutoMirrored.Rounded.Backspace,
                                tr("Usuń cyfrę · przytrzymaj: wyczyść"),
                                tint = Palette.TextDim,
                                onLongPress = { vm.dialed = "" },
                            ) { vm.dialed = vm.dialed.dropLast(1) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(label, pressScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Fg.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Fg, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Pływający przycisk rozwijający klawiaturę, gdy jest zwinięta. */
@Composable
fun DialpadFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .reactive(tr("Pokaż klawiaturę"), pressScale = 0.9f, onClick = onClick)
            .size(62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Answer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Dialpad, null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
