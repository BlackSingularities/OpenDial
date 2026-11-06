package com.blacksingularities.opencall.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import com.blacksingularities.opencall.ui.theme.reactive
import com.blacksingularities.opencall.ui.theme.Palette
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.PageHeader

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesTab(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit, onBack: (() -> Unit)? = null) {
    val starred = remember(vm.contacts) { vm.contacts.filter { it.starred && it.numbers.isNotEmpty() } }
    // „Często”: kontakty, do których dzwoniono najczęściej w ostatnich 30 dniach (bez ulubionych).
    val frequent = remember(vm.calls, vm.contacts, vm.settings.showFrequent) {
        if (!vm.settings.showFrequent) {
            emptyList()
        } else {
            CallLogRepo.frequent(vm.calls, System.currentTimeMillis() - 30L * 86_400_000L, 16)
                .mapNotNull { vm.contactFor(it) }
                .filter { !it.starred }
                .distinctBy { it.id }
                .take(8)
        }
    }
    var menu by remember { mutableStateOf<Contact?>(null) }
    // Najbliższe urodziny i rocznice (tydzień naprzód) – tylko z włączonymi przypomnieniami.
    var events by remember { mutableStateOf<List<com.blacksingularities.opencall.data.ContactEvent>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(vm.contacts, vm.settings.eventReminders, vm.hasContacts) {
        events = if (vm.settings.eventReminders && vm.hasContacts) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.blacksingularities.opencall.data.ContactEvents.upcoming(vm.getApplication(), 7)
            }
        } else {
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        if (onBack != null) PageHeader(tr("Ulubione"), onBack) else TabHeader(vm, leading = { NavChips(vm) })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SetupCard(vm, onRequestRole, onRequestPermissions) }
            if (!vm.hasContacts) {
                item(span = { GridItemSpan(maxLineSpan) }) { NoPermission(tr("Ulubione wymagają dostępu do kontaktów."), onRequestPermissions) }
                return@LazyVerticalGrid
            }
            if (events.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { EventsCard(vm, events) }
            }
            if (starred.isEmpty() && vm.loaded) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Rounded.StarBorder,
                        tr("Brak ulubionych"),
                        tr("Oznacz kontakt gwiazdką w jego szczegółach – pojawi się tutaj do szybkiego dzwonienia."),
                    )
                }
            }
            items(starred, key = { "s${it.id}" }) { c ->
                FavoriteTile(c, onClick = { c.primaryNumber?.let { vm.call(it.number, c.name) } }, onLong = { menu = c })
            }
            if (frequent.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { ListHeader(tr("Często"), Modifier.padding(top = 8.dp)) }
                items(frequent, key = { "f${it.id}" }) { c ->
                    FavoriteTile(c, onClick = { c.primaryNumber?.let { vm.call(it.number, c.name) } }, onLong = { menu = c })
                }
            }
        }
    }

    menu?.let { c ->
        ChoiceDialog(
            c.name,
            buildList {
                c.numbers.forEach { add(Icons.Rounded.Call to tr("Zadzwoń: {0}", PhoneText.pretty(it.number))) }
                add(Icons.AutoMirrored.Rounded.Message to tr("Wyślij SMS"))
                add(Icons.Rounded.Info to tr("Szczegóły kontaktu"))
                add(Icons.Rounded.StarOutline to if (c.starred) tr("Usuń z ulubionych") else tr("Dodaj do ulubionych"))
            },
            onDismiss = { menu = null },
        ) { i ->
            menu = null
            val n = c.numbers.size
            when {
                i < n -> vm.call(c.numbers[i].number, c.name)
                i == n -> c.primaryNumber?.let { vm.sms(it.number) }
                i == n + 1 -> vm.push(Route.ContactInfo(c.id))
                else -> vm.setStarred(c.id, !c.starred)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(c: Contact, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(c.name, c.photoUri, 72.dp)
        Spacer(Modifier.height(8.dp))
        Text(c.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 16.sp)
    }
}

/** Pasek okazji: „Dziś urodziny: Anna · kończy 34 lata”, niżej najbliższe dni. Dotknięcie otwiera kontakt. */
@Composable
private fun EventsCard(vm: AppViewModel, events: List<com.blacksingularities.opencall.data.ContactEvent>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(Palette.Fg.copy(alpha = 0.06f))
            .padding(vertical = 6.dp),
    ) {
        val today = events.filter { it.days == 0 }
        Text(
            when {
                today.size == 1 && today[0].birthday -> tr("Dziś urodziny: {0}", today[0].name)
                today.isNotEmpty() -> tr("Dzisiejsze okazje: {0}", today.size)
                else -> tr("Najbliższe okazje")
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
        events.take(5).forEach { e ->
            val whenText = when (e.days) {
                0 -> tr("dziś")
                1 -> tr("jutro")
                else -> tr("za {0} dni", e.days)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .reactive(e.name, pressScale = 0.98f) { vm.push(Route.ContactInfo(e.contactId)) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(e.name, e.photoUri, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        com.blacksingularities.opencall.reminders.EventReminders.describe(e) + " · " + whenText,
                        fontSize = 12.sp,
                        color = if (e.days == 0) Palette.Fg else Palette.TextDim,
                        maxLines = 1,
                    )
                }
                Icon(if (e.birthday) Icons.Rounded.Cake else Icons.Rounded.Event, null, tint = Palette.TextDim, modifier = Modifier.size(18.dp))
            }
        }
    }
}
