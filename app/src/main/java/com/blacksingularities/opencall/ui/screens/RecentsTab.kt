package com.blacksingularities.opencall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CallGroup
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.RecentTap
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ChipRow
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.components.TabChip
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

/** Filtr listy „Ostatnie”. */
private enum class RecentFilter { ALL, MISSED, INCOMING, OUTGOING, BLOCKED }

@Composable
fun RecentsTab(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(RecentFilter.ALL) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<CallGroup?>(null) }

    val list = remember(vm.groups, filter, query) {
        val q = PhoneText.fold(query.trim())
        vm.groups.filter { g ->
            val k = g.first.kind
            // Filtry są zakryte przez pole wyszukiwania, więc szukamy w całej historii.
            val passFilter = q.isNotEmpty() || when (filter) {
                RecentFilter.ALL -> true
                RecentFilter.MISSED -> k == CallKind.MISSED || k == CallKind.REJECTED
                RecentFilter.INCOMING -> k == CallKind.INCOMING
                RecentFilter.OUTGOING -> k == CallKind.OUTGOING
                RecentFilter.BLOCKED -> k == CallKind.BLOCKED
            }
            passFilter && (q.isEmpty() || PhoneText.fold(g.first.name ?: "").contains(q) || PhoneText.numberMatches(g.first.number, q))
        }
    }

    BackHandler(enabled = vm.keypadOpen && vm.stack.isEmpty()) { vm.keypadOpen = false }
    // Przewinięcie historii palcem zwija pustą klawiaturę – jak w Samsungu i Huaweiu.
    val collapseOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > 4f && vm.keypadOpen && vm.dialed.isEmpty()) vm.keypadOpen = false
                return Offset.Zero
            }
        }
    }
    val typing = vm.keypadOpen && vm.dialed.isNotEmpty()

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        SearchHeader(
            vm, searching,
            onSearching = {
                searching = it
                // Pole wyszukiwania ma własną klawiaturę – panel wybierania by ją dublował.
                if (it) vm.keypadOpen = false
            },
            query, { query = it }, tr("Szukaj w historii"),
            leading = {
                if (!typing) ChipRow {
                    TabChip(Icons.Rounded.History, tr("Wszystkie"), filter == RecentFilter.ALL, compact = true) { filter = RecentFilter.ALL }
                    TabChip(Icons.AutoMirrored.Rounded.CallMissed, tr("Nieodebrane"), filter == RecentFilter.MISSED, compact = true) { filter = RecentFilter.MISSED }
                    TabChip(Icons.AutoMirrored.Rounded.CallReceived, tr("Przychodzące"), filter == RecentFilter.INCOMING, compact = true) { filter = RecentFilter.INCOMING }
                    TabChip(Icons.AutoMirrored.Rounded.CallMade, tr("Wychodzące"), filter == RecentFilter.OUTGOING, compact = true) { filter = RecentFilter.OUTGOING }
                    TabChip(Icons.Rounded.Block, tr("Zablokowane"), filter == RecentFilter.BLOCKED, compact = true) { filter = RecentFilter.BLOCKED }
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (typing) {
            DialSuggestions(vm, Modifier.fillMaxSize())
        } else if (!vm.hasCallLog) {
            Column {
                SetupCard(vm, onRequestRole, onRequestPermissions, Modifier.padding(14.dp))
                NoPermission(tr("Historia połączeń wymaga dostępu do rejestru połączeń."), onRequestPermissions)
            }
        } else LazyColumn(Modifier.fillMaxSize().nestedScroll(collapseOnScroll), contentPadding = PaddingValues(bottom = 96.dp)) {
            item { SetupCard(vm, onRequestRole, onRequestPermissions, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
            if (list.isEmpty() && vm.loaded) {
                item {
                    EmptyState(
                        Icons.Rounded.History,
                        if (query.isNotBlank() || filter != RecentFilter.ALL) tr("Nic nie znaleziono") else tr("Brak połączeń"),
                        if (query.isNotBlank() || filter != RecentFilter.ALL) tr("Zmień filtr albo wyszukiwaną frazę.") else tr("Tu pojawi się historia rozmów."),
                    )
                }
            }
            var lastBucket = ""
            list.forEach { g ->
                val bucket = Format.bucket(g.first.date)
                if (bucket != lastBucket) {
                    lastBucket = bucket
                    item(key = "h_$bucket") { ListHeader(bucket) }
                }
                item(key = g.key) {
                    CallRow(
                        vm, g,
                        expanded = expanded == g.key,
                        onTap = {
                            when (vm.settings.recentTap) {
                                RecentTap.CALL -> if (!g.first.hidden) vm.call(g.first.number, g.first.name)
                                RecentTap.DETAILS -> openDetails(vm, g)
                                RecentTap.EXPAND -> expanded = if (expanded == g.key) null else g.key
                            }
                        },
                        onLong = { menu = g },
                    )
                }
            }
        }
            androidx.compose.animation.AnimatedVisibility(
                visible = !vm.keypadOpen && !searching,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { DialpadFab({ vm.keypadOpen = true }) }
        }
        AnimatedVisibility(
            visible = vm.keypadOpen,
            // Wysokość zmienia się płynnie razem z wysuwaniem, więc lista nad klawiaturą rośnie
            // na bieżąco i nie zostaje pusta dziura, która dopiero potem się wypełnia.
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) +
                slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(180)),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top) +
                slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeOut(tween(150)),
        ) { DialerPanel(vm, onCollapse = { vm.keypadOpen = false }) }
    }

    menu?.let { g -> CallMenu(vm, g) { menu = null } }
}

private fun openDetails(vm: AppViewModel, g: CallGroup) {
    val c = vm.contactFor(g.first.number)
    if (c != null) vm.push(Route.ContactInfo(c.id)) else if (!g.first.hidden) vm.push(Route.NumberInfo(g.first.number))
}

fun kindIcon(k: CallKind): ImageVector = when (k) {
    CallKind.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived
    CallKind.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade
    CallKind.MISSED -> Icons.AutoMirrored.Rounded.CallMissed
    CallKind.REJECTED -> Icons.Rounded.CallEnd
    CallKind.BLOCKED -> Icons.Rounded.Block
    CallKind.VOICEMAIL -> Icons.Rounded.Voicemail
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(vm: AppViewModel, g: CallGroup, expanded: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val e = g.first
    val missed = g.missed
    val title = when {
        e.hidden -> tr("Numer prywatny")
        e.name != null -> e.name
        else -> PhoneText.pretty(e.number)
    }
    val simLabel = if (vm.settings.showSim && vm.sims.size > 1) vm.sims.firstOrNull { it.id == e.accountId }?.let { "SIM ${it.slot + 1}" } else null
    Column(
        Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (expanded) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.reactive(tr("Szczegóły")) { openDetails(vm, g) }) {
                Avatar(if (e.hidden || e.name == null) null else e.name, e.photoUri, 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = if (g.unseenMissed) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (missed) Palette.Record else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (g.count > 1) Text("  (${g.count})", fontSize = 14.sp, color = if (missed) Palette.Record else Palette.TextDim)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kindIcon(e.kind), null, tint = if (missed) Palette.Record else Palette.TextDim, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    val parts = buildList {
                        if (e.video) add(tr("Wideo"))
                        if (e.name != null && !e.hidden) vm.contactFor(e.number)?.numbers?.firstOrNull { PhoneText.sameNumber(it.number, e.number) }?.let { add(Format.phoneType(it.type, it.label)) }
                        else if (vm.settings.showCallerLocation) e.location?.let { add(it) }
                        simLabel?.let { add(it) }
                        add(Format.relative(e.date))
                    }
                    Text(parts.joinToString(" · "), fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!e.hidden) {
                RoundIcon(Icons.Rounded.Call, tr("Zadzwoń"), size = 40.dp) { vm.call(e.number, e.name) }
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 66.dp, end = 10.dp, bottom = 10.dp)) {
                g.entries.take(6).forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(kindIcon(x.kind), null, tint = if (x.kind == CallKind.MISSED) Palette.Record else Palette.TextDim, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            Format.time(x.date) + " · " + Format.kind(x.kind) + if (x.durationSec > 0) " · " + PhoneText.duration(x.durationSec) else "",
                            fontSize = 12.sp,
                            color = Palette.TextDim,
                        )
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!e.hidden) {
                        QuickAction(Icons.AutoMirrored.Rounded.Message, tr("SMS")) { vm.sms(e.number) }
                        if (vm.contactFor(e.number) == null) QuickAction(Icons.Rounded.PersonAdd, tr("Dodaj")) { vm.newContact(e.number) }
                    }
                    QuickAction(Icons.Rounded.Info, tr("Szczegóły")) { openDetails(vm, g) }
                    QuickAction(Icons.Rounded.Delete, tr("Usuń")) { vm.deleteCalls(g.entries.map { it.id }) }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .reactive(label, pressScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Menu wpisu (przytrzymanie): kopiuj, SMS, dodaj, zablokuj, usuń. */
@Composable
private fun CallMenu(vm: AppViewModel, g: CallGroup, onDismiss: () -> Unit) {
    val e = g.first
    val inContacts = vm.contactFor(e.number) != null
    val blocked = vm.isBlocked(e.number)
    DarkDialog(
        if (e.hidden) tr("Numer prywatny") else e.name ?: PhoneText.pretty(e.number),
        onDismiss = onDismiss,
        buttons = { PillButton(tr("Zamknij"), primary = false, onClick = onDismiss) },
    ) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
            fun go(f: () -> Unit) {
                onDismiss()
                f()
            }
            if (!e.hidden) {
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.Call, tr("Zadzwoń"), PhoneText.pretty(e.number)) { go { vm.call(e.number, e.name) } }
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.AutoMirrored.Rounded.Message, tr("Wyślij SMS")) { go { vm.sms(e.number) } }
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.ContentCopy, tr("Kopiuj numer")) { go { vm.copy(e.number) } }
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.Call, tr("Edytuj numer przed połączeniem")) {
                    go {
                        vm.dialed = PhoneText.digits(e.number)
                        vm.openKeypad()
                    }
                }
                if (!inContacts) com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.PersonAdd, tr("Dodaj do kontaktów")) { go { vm.newContact(e.number) } }
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.Block, if (blocked) tr("Odblokuj numer") else tr("Zablokuj numer"), danger = !blocked) {
                    go { if (blocked) vm.unblock(e.number) else vm.block(e.number) }
                }
            }
            com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.Delete, tr("Usuń z historii"), if (g.count > 1) tr("{0} wpisy", g.count) else null, danger = true) {
                go { vm.deleteCalls(g.entries.map { it.id }) }
            }
            if (!e.hidden) {
                com.blacksingularities.opencall.ui.components.ActionRow(Icons.Rounded.DeleteSweep, tr("Usuń całą historię tego numeru"), danger = true) {
                    go { vm.deleteCalls(vm.calls.filter { PhoneText.sameNumber(it.number, e.number) }.map { it.id }) }
                }
            }
        }
    }
}
