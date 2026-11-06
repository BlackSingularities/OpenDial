package com.blacksingularities.opencall.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.blacksingularities.opencall.ui.theme.reactive
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun ContactsTab(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Rozsuwanie palców płynnie powiększa listę (jak w OpenSMS), a dalej zamienia ją w kafelki.
    val zoom = rememberListZoom(vm.settings.contactScale)
    val columns = zoom.columns

    val filtered = remember(vm.contacts, query, vm.settings.onlyWithNumbers) {
        val q = PhoneText.fold(query.trim())
        vm.contacts.filter { c ->
            (!vm.settings.onlyWithNumbers || c.numbers.isNotEmpty()) &&
                (q.isEmpty() || c.searchName.contains(q) || c.numbers.any { PhoneText.numberMatches(it.number, q) })
        }
    }
    // Pozycje nagłówków liter na liście – do szybkiego przewijania paskiem liter.
    val sections = remember(filtered) {
        val out = LinkedHashMap<Char, Int>()
        var index = 0
        var last: Char? = null
        filtered.forEach { c ->
            val l = PhoneText.indexLetter(c.name)
            if (l != last) {
                out[l] = index
                index++
                last = l
            }
            index++
        }
        out
    }
    val selecting = selection.isNotEmpty()

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.Close, tr("Anuluj zaznaczenie")) { selection = emptySet() }
                Spacer(Modifier.width(12.dp))
                Text(tr("Zaznaczono: {0}", selection.size), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Rounded.CheckCircle, tr("Zaznacz wszystkie")) { selection = filtered.map { it.id }.toSet() }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Rounded.Star, tr("Dodaj do ulubionych")) {
                    selection.forEach { vm.setStarred(it, true) }
                    selection = emptySet()
                }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Icons.Rounded.Delete, tr("Usuń zaznaczone"), tint = Palette.Record) { confirmDelete = true }
            }
        } else {
            SearchHeader(vm, searching, { searching = it }, query, { query = it }, tr("Szukaj wśród {0} kontaktów", vm.contacts.size), leading = { NavChips(vm) }) {
                if (!searching) RoundIcon(Icons.Rounded.PersonAdd, tr("Nowy kontakt"), enabled = vm.hasContacts, size = HeaderButtonSize, iconSize = HeaderIconSize) { vm.newContact() }
            }
        }
        if (!vm.hasContacts) {
            SetupCard(vm, onRequestRole, onRequestPermissions, Modifier.padding(14.dp))
            NoPermission(tr("Lista kontaktów wymaga dostępu do kontaktów."), onRequestPermissions)
            return@Column
        }
        Box(
            Modifier
                .fillMaxSize()
                .pinchZoom(zoom, vm.settings.haptics) { v -> vm.updateSettings { it.copy(contactScale = v) } },
        ) {
            ZoomedDensity(zoom) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, end = 22.dp, start = if (columns == 1) 0.dp else 10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { SetupCard(vm, onRequestRole, onRequestPermissions, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
                if (filtered.isEmpty() && vm.loaded) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            Icons.Rounded.Contacts,
                            if (query.isBlank()) tr("Brak kontaktów") else tr("Nic nie znaleziono"),
                            if (query.isBlank()) tr("Dodaj pierwszy kontakt albo zaimportuj plik vCard w ustawieniach.") else tr("Spróbuj innej nazwy lub numeru."),
                            action = if (query.isBlank()) tr("Nowy kontakt") else null,
                            onAction = { vm.newContact() },
                        )
                    }
                }
                var last: Char? = null
                filtered.forEach { c ->
                    val l = PhoneText.indexLetter(c.name)
                    if (l != last) {
                        last = l
                        item(key = "h_$l", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { ListHeader(l.toString(), Modifier.animateItem()) }
                    }
                    item(key = c.id, contentType = "contact") {
                        val onClick: () -> Unit = {
                            if (selecting) {
                                selection = if (c.id in selection) selection - c.id else selection + c.id
                            } else {
                                vm.push(Route.ContactInfo(c.id))
                            }
                        }
                        val onLong: () -> Unit = { selection = selection + c.id }
                        if (columns == 1) {
                            Box(Modifier.animateItem()) {
                                ContactRow(vm, c, selected = c.id in selection, selecting = selecting, onClick = onClick, onLong = onLong)
                            }
                        } else {
                            ContactTile(vm, c, columns, selected = c.id in selection, selecting = selecting, onClick = onClick, onLong = onLong, modifier = Modifier.animateItem())
                        }
                    }
                }
            }
            }
            ZoomPill(zoom, Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                zoom.scale = 1f
                vm.updateSettings { it.copy(contactScale = 1f) }
            }
            if (sections.size > 4 && query.isBlank()) {
                // +1: pierwszy element listy to karta konfiguracji.
                AlphabetBar(sections.keys.toList(), Modifier.align(Alignment.CenterEnd)) { ch ->
                    sections[ch]?.let { scope.launch { listState.scrollToItem(it + 1) } }
                }
            }
        }
    }

    if (confirmDelete) {
        DarkDialog(
            tr("Usunąć kontakty?"),
            onDismiss = { confirmDelete = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { confirmDelete = false }
                PillButton(tr("Usuń"), danger = true) {
                    vm.deleteContacts(selection)
                    selection = emptySet()
                    confirmDelete = false
                }
            },
        ) {
            Text(tr("Zaznaczone kontakty ({0}) zostaną usunięte z telefonu i zsynchronizowanych kont.", selection.size), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactRow(
    vm: AppViewModel,
    c: Contact,
    selected: Boolean = false,
    selecting: Boolean = false,
    subtitle: String? = null,
    avatar: Dp = 42.dp,
    onClick: () -> Unit,
    onLong: () -> Unit = {},
) {
    // Tekst i odstępy rosną razem z awatarem; w najgęstszym układzie zostaje sama nazwa.
    val scale = avatar / 42.dp
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Palette.Fg.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 10.dp, vertical = 7.dp * scale.coerceAtMost(1.3f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (selected) Palette.Fg else Palette.TextFaint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Avatar(c.name, c.photoUri, avatar)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = (16f * scale.coerceIn(0.85f, 1.3f)).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.starred) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.Star, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
                }
            }
            val sub = subtitle ?: c.primaryNumber?.let { "${Format.phoneType(it.type, it.label)} · ${PhoneText.pretty(it.number)}" }
            if (sub != null && avatar > 30.dp) Text(sub, fontSize = (12f * scale.coerceIn(0.9f, 1.25f)).sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val n = c.primaryNumber
        if (n != null && !selecting) {
            RoundIcon(Icons.Rounded.Call, tr("Zadzwoń do {0}", c.name), size = (avatar - 4.dp).coerceIn(30.dp, 48.dp)) { vm.call(n.number, c.name) }
        }
    }
}

/**
 * Kafelek kontaktu (2–4 w rzędzie): duże zdjęcie albo inicjały, nazwa pod spodem, gwiazdka
 * ulubionego i – w większych kafelkach – przycisk szybkiego połączenia na zdjęciu.
 * Wysokość tekstu jest stała, więc kafelki w rzędzie mają równe tła.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactTile(
    vm: AppViewModel,
    c: Contact,
    columns: Int,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val big = columns == 2
    val small = columns >= 4
    Column(
        modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(if (big) 26.dp else 20.dp))
            .background(if (selected) Palette.Fg.copy(alpha = 0.16f) else Palette.Fg.copy(alpha = 0.05f))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(start = 8.dp, end = 8.dp, top = if (big) 14.dp else 10.dp, bottom = if (big) 12.dp else 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = maxWidth * if (big) 0.8f else 0.88f
            Box(Modifier.size(side)) {
                Avatar(c.name, c.photoUri, side)
                val badge = if (big) 26.dp else if (small) 18.dp else 22.dp
                when {
                    selecting -> Box(
                        Modifier.align(Alignment.TopEnd).size(badge).clip(CircleShape).background(Palette.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            null,
                            tint = if (selected) Palette.Fg else Palette.TextFaint,
                            modifier = Modifier.size(badge - 2.dp),
                        )
                    }
                    c.starred -> Box(
                        Modifier.align(Alignment.TopEnd).size(badge).clip(CircleShape).background(Palette.Ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = Palette.Fg, modifier = Modifier.size(badge * 0.62f))
                    }
                }
                val n = c.primaryNumber
                if (!small && !selecting && n != null) {
                    val call = if (big) 40.dp else 32.dp
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(call)
                            .clip(CircleShape)
                            .background(Palette.Fg)
                            .reactive(tr("Zadzwoń do {0}", c.name), pressScale = 0.85f) { vm.call(n.number, c.name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Call, null, tint = Palette.Ink, modifier = Modifier.size(call * 0.5f))
                    }
                }
            }
        }
        Spacer(Modifier.height(if (big) 10.dp else if (small) 5.dp else 7.dp))
        Text(
            c.name,
            fontSize = when {
                big -> 15.sp
                small -> 11.sp
                else -> 13.sp
            },
            fontWeight = FontWeight.SemiBold,
            maxLines = if (small) 1 else 2,
            minLines = if (small) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = when {
                big -> 18.sp
                small -> 13.sp
                else -> 16.sp
            },
        )
        if (big) {
            Text(
                c.primaryNumber?.let { "${Format.phoneType(it.type, it.label)} · ${PhoneText.pretty(it.number)}" }.orEmpty(),
                fontSize = 12.sp,
                color = Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Pionowy pasek liter: dotknięcie lub przeciągnięcie przewija listę do sekcji. */
@Composable
private fun AlphabetBar(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    var height by remember { mutableStateOf(1) }
    var active by remember { mutableStateOf<Char?>(null) }
    fun at(y: Float): Char = letters[((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)]
    Box(modifier.fillMaxHeight().padding(vertical = 12.dp, horizontal = 2.dp)) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(20.dp)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    detectTapGestures { o -> onLetter(at(o.y)) }
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> active = at(o.y).also(onLetter) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ ->
                        val ch = at(change.position.y)
                        if (ch != active) {
                            active = ch
                            onLetter(ch)
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(l.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (l == active) Palette.Fg else Palette.TextDim)
            }
        }
        active?.let { l ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(end = 40.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Palette.Fg),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.toString(), color = Palette.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
