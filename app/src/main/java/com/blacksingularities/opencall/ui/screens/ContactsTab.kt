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
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Układ: 1 = lista, 2–4 = kafelki. Szczypanie przechodzi płynnie między poziomami.
    var columns by remember { mutableStateOf(vm.settings.contactColumns.coerceIn(1, 4)) }
    val zoom = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    var gridWidth by remember { mutableStateOf(1) }

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
            SearchHeader(vm, searching, { searching = it }, query, { query = it }, tr("Szukaj wśród {0} kontaktów", vm.contacts.size)) {
                if (!searching) RoundIcon(Icons.Rounded.PersonAdd, tr("Nowy kontakt"), enabled = vm.hasContacts) { vm.newContact() }
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
                .onSizeChanged { gridWidth = it.width.coerceAtLeast(1) }
                .pinchToZoom(
                    onStart = { c, size ->
                        origin = TransformOrigin((c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
                        scope.launch { zoom.stop(); fade.stop() }
                    },
                    onPinch = { acc, c ->
                        val level = ZoomLevels.indexOf(columns)
                        val canIn = level < ZoomLevels.lastIndex
                        val canOut = level > 0
                        when {
                            acc > 1.32f && canIn || acc < 0.76f && canOut -> {
                                val next = ZoomLevels[level + if (acc > 1f) 1 else -1]
                                val w = with(density) { gridWidth.toDp() }
                                val ratio = tileSize(next, w) / tileSize(columns, w)
                                // Element pod palcami zostaje pod palcami po zmianie układu.
                                val anchor = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    c.y >= it.offset.y && c.y < it.offset.y + it.size.height && (columns == 1 || (c.x >= it.offset.x && c.x < it.offset.x + it.size.width))
                                } ?: listState.layoutInfo.visibleItemsInfo.firstOrNull()
                                val anchorTop = anchor?.offset?.y?.toFloat() ?: 0f
                                val visual = zoom.value
                                columns = next
                                vm.updateSettings { it.copy(contactColumns = next) }
                                if (vm.settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    zoom.snapTo((visual / ratio).coerceIn(0.55f, 1.8f))
                                    fade.snapTo(0.35f)
                                    launch { fade.animateTo(1f, tween(260)) }
                                    zoom.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
                                }
                                if (anchor != null) {
                                    scope.launch {
                                        listState.scrollToItem(anchor.index)
                                        listState.scrollBy(-(anchorTop * ratio.coerceAtMost(1f)))
                                    }
                                }
                                true
                            }
                            else -> {
                                // Na krańcach zakresu – opór gumki zamiast zmiany.
                                val damp = if (acc > 1f && !canIn || acc < 1f && !canOut) 0.12f else 0.55f
                                scope.launch { zoom.snapTo(1f + (acc - 1f) * damp) }
                                false
                            }
                        }
                    },
                    onEnd = {
                        scope.launch { zoom.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)) }
                    },
                ),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = listState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                    alpha = fade.value
                    transformOrigin = origin
                },
                contentPadding = PaddingValues(bottom = 16.dp, end = 22.dp, start = if (columns == 1) 0.dp else 8.dp),
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
                        item(key = "h_$l", span = { GridItemSpan(maxLineSpan) }) { ListHeader(l.toString(), Modifier.animateItem()) }
                    }
                    item(key = c.id) {
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
                            ContactTile(c, columns, selected = c.id in selection, selecting = selecting, onClick = onClick, onLong = onLong, modifier = Modifier.animateItem())
                        }
                    }
                }
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
    onClick: () -> Unit,
    onLong: () -> Unit = {},
) {
    Row(
        Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (selected) Color.White else Palette.TextFaint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Avatar(c.name, c.photoUri, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.starred) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.Star, null, tint = Palette.TextDim, modifier = Modifier.size(14.dp))
                }
            }
            val sub = subtitle ?: c.primaryNumber?.let { "${Format.phoneType(it.type, it.label)} · ${PhoneText.pretty(it.number)}" }
            if (sub != null) Text(sub, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val n = c.primaryNumber
        if (n != null && !selecting) {
            RoundIcon(Icons.Rounded.Call, tr("Zadzwoń do {0}", c.name), size = 38.dp) { vm.call(n.number, c.name) }
        }
    }
}

/** Kolejność poziomów przybliżenia: lista, potem coraz większe kafelki. */
private val ZoomLevels = listOf(1, 4, 3, 2)

/** Przybliżony rozmiar awatara na danym poziomie – do płynnego przejścia między układami. */
private fun tileSize(columns: Int, width: Dp): Dp =
    if (columns == 1) 42.dp else ((width - 30.dp) / columns - 20.dp).coerceAtLeast(24.dp)

/**
 * Szczypanie dwoma palcami. [onPinch] dostaje łączne przybliżenie od początku (lub od ostatniej
 * zmiany układu) i zwraca true, gdy układ się zmienił – wtedy licznik zaczyna od nowa.
 * Jeden palec przewija listę jak zwykle.
 */
private fun Modifier.pinchToZoom(
    onStart: (Offset, IntSize) -> Unit,
    onPinch: (Float, Offset) -> Boolean,
    onEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var acc = 1f
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val centroid = event.calculateCentroid(useCurrent = true)
                if (!pinching) {
                    pinching = true
                    onStart(centroid, size)
                }
                acc *= event.calculateZoom()
                if (onPinch(acc, centroid)) acc = 1f
                event.changes.forEach { it.consume() }
            } else if (pinching) {
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) onEnd()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactTile(
    c: Contact,
    columns: Int,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(if (columns == 2) 24.dp else 18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        val side = (maxWidth - 8.dp).coerceAtLeast(24.dp)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(c.name, c.photoUri, side)
                if (selecting) {
                    Icon(
                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        null,
                        tint = if (selected) Color.White else Palette.TextFaint,
                        modifier = Modifier.align(Alignment.TopEnd).size(if (columns == 4) 18.dp else 24.dp),
                    )
                } else if (c.starred) {
                    Icon(Icons.Rounded.Star, null, tint = Palette.Amber, modifier = Modifier.align(Alignment.TopEnd).size(if (columns == 4) 14.dp else 18.dp))
                }
            }
            Spacer(Modifier.height(if (columns == 4) 5.dp else 8.dp))
            Text(
                c.name,
                fontSize = when (columns) { 2 -> 15.sp; 3 -> 13.sp; else -> 11.sp },
                fontWeight = FontWeight.Medium,
                maxLines = if (columns == 4) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = when (columns) { 2 -> 18.sp; 3 -> 16.sp; else -> 13.sp },
            )
            if (columns == 2) {
                c.primaryNumber?.let {
                    Text(PhoneText.pretty(it.number), fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
                Text(l.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (l == active) Color.White else Palette.TextDim)
            }
        }
        active?.let { l ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(end = 40.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(l.toString(), color = Palette.Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
