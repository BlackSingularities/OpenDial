package com.blacksingularities.opencall.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CallLogRepo
import com.blacksingularities.opencall.data.Contact
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Avatar
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.ChipRow
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.Group
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.components.SwitchRow
import com.blacksingularities.opencall.ui.components.TabChip
import com.blacksingularities.opencall.ui.theme.Palette
import java.text.DateFormatSymbols

/** Zablokowane numery (lista systemowa) i filtry: ukryte, nieznane, reguły prefiksów. */
@Composable
fun BlockedScreen(vm: AppViewModel) {
    val s = vm.settings
    var adding by remember { mutableStateOf(false) }
    var addingRule by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    Page(tr("Blokowanie"), onBack = { vm.pop() }, actions = {
        RoundIcon(Icons.Rounded.Add, tr("Zablokuj numer"), enabled = vm.isDefaultDialer) {
            input = ""
            adding = true
        }
    }) {
        if (!vm.isDefaultDialer) {
            Text(tr("Blokowanie działa, gdy OpenDial jest domyślną aplikacją telefonu."), color = Palette.Record, fontSize = 13.sp, modifier = Modifier.padding(6.dp))
        }
        Group(tr("Filtry")) {
            SwitchRow(Icons.Rounded.VisibilityOff, tr("Blokuj numery ukryte"), tr("Połączenia bez numeru („Numer prywatny”)"), s.blockHidden) { v -> vm.updateSettings { it.copy(blockHidden = v) } }
            SwitchRow(Icons.Rounded.PersonOff, tr("Blokuj spoza kontaktów"), tr("Odrzucaj numery, których nie masz w kontaktach"), s.blockUnknown) { v -> vm.updateSettings { it.copy(blockUnknown = v) } }
            SwitchRow(Icons.Rounded.NotificationsOff, tr("Wyciszaj spoza kontaktów"), tr("Nieznane numery dzwonią bez dźwięku – możesz odebrać"), s.silenceUnknown, enabled = !s.blockUnknown) { v -> vm.updateSettings { it.copy(silenceUnknown = v) } }
            SwitchRow(Icons.Rounded.History, tr("Zapisuj w historii"), tr("Zablokowane połączenia widoczne w „Ostatnie”"), s.logBlocked) { v -> vm.updateSettings { it.copy(logBlocked = v) } }
            SwitchRow(Icons.Rounded.NotificationsActive, tr("Powiadamiaj o blokadzie"), tr("Ciche powiadomienie przy każdym odrzuceniu przez filtr"), s.notifyBlocked) { v -> vm.updateSettings { it.copy(notifyBlocked = v) } }
        }
        Group(tr("Reguły prefiksów")) {
            if (s.blockRules.isEmpty()) {
                Text(tr("Np. „0700” (linie płatne), „+48 22 30*” (telemarketing). Gwiazdka zastępuje dowolne cyfry."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
            s.blockRules.forEach { r ->
                ActionRow(Icons.Rounded.FilterAlt, r, tr("Dotknij, aby usunąć")) { vm.updateSettings { it.copy(blockRules = it.blockRules - r) } }
            }
            ActionRow(Icons.Rounded.Add, tr("Dodaj regułę")) {
                input = ""
                addingRule = true
            }
        }
        Group(tr("Zablokowane numery · {0}", vm.blocked.size)) {
            if (vm.blocked.isEmpty()) {
                Text(tr("Brak. Numer zablokujesz z historii połączeń, ze szczegółów kontaktu albo przyciskiem +."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
            vm.blocked.forEach { n ->
                val c = vm.contactFor(n)
                ActionRow(Icons.Rounded.Block, c?.name ?: PhoneText.pretty(n), if (c != null) PhoneText.pretty(n) else tr("Dotknij, aby odblokować"), danger = true) { vm.unblock(n) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (adding || addingRule) {
        DarkDialog(
            if (adding) tr("Zablokuj numer") else tr("Nowa reguła"),
            onDismiss = {
                adding = false
                addingRule = false
            },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) {
                    adding = false
                    addingRule = false
                }
                PillButton(if (adding) tr("Zablokuj") else tr("Dodaj"), danger = adding, enabled = input.any { it.isDigit() }) {
                    if (adding) vm.block(input.trim()) else vm.updateSettings { it.copy(blockRules = (it.blockRules + input.trim()).distinct()) }
                    adding = false
                    addingRule = false
                }
            },
        ) {
            DarkField(input, { input = it.filter { c -> c.isDigit() || c in "+* " } }, if (adding) tr("Numer telefonu") else tr("Prefiks, np. 0700 albo +48 800*"), keyboard = KeyboardType.Phone)
        }
    }
}

/** Statystyki z rejestru połączeń za wybrany okres. */
@Composable
fun StatsScreen(vm: AppViewModel) {
    val periods = listOf(7 to tr("7 dni"), 30 to tr("30 dni"), 90 to tr("3 miesiące"), 365 to tr("Rok"), 0 to tr("Wszystko"))
    var period by remember { mutableIntStateOf(30) }
    val stats = remember(vm.calls, period) {
        val since = if (period == 0) 0L else System.currentTimeMillis() - period * 86_400_000L
        CallLogRepo.stats(vm.calls, since) { e -> e.name ?: PhoneText.pretty(e.number) }
    }
    val h = tr("h")
    val min = tr("min")
    val sec = tr("s")
    Page(tr("Statystyki"), onBack = { vm.pop() }) {
        ChipRow { periods.forEach { (d, label) -> TabChip(null, label, period == d) { period = d } } }
        if (stats.total == 0) {
            EmptyState(Icons.Rounded.History, tr("Brak danych"), tr("W tym okresie nie było połączeń."))
            return@Page
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(tr("Połączenia"), stats.total.toString(), Modifier.weight(1f))
            StatTile(tr("Czas rozmów"), PhoneText.durationShort(stats.talkSec, h, min, sec), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(tr("Przychodzące"), stats.incoming.toString(), Modifier.weight(1f), Icons.AutoMirrored.Rounded.CallReceived)
            StatTile(tr("Wychodzące"), stats.outgoing.toString(), Modifier.weight(1f), Icons.AutoMirrored.Rounded.CallMade)
            StatTile(tr("Nieodebrane"), stats.missed.toString(), Modifier.weight(1f), Icons.AutoMirrored.Rounded.CallMissed, Palette.Record)
        }
        val talks = stats.incoming + stats.outgoing
        if (talks > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(tr("Średnia rozmowa"), PhoneText.durationShort(stats.talkSec / talks, h, min, sec), Modifier.weight(1f), Icons.Rounded.Timer)
                stats.longest?.let { StatTile(tr("Najdłuższa"), PhoneText.duration(it.durationSec), Modifier.weight(1f), Icons.Rounded.Timer) }
            }
        }
        if (stats.top.isNotEmpty()) {
            Group(tr("Najczęściej")) {
                val max = stats.top.first().second.coerceAtLeast(1)
                stats.top.forEach { (name, count) -> BarRow(name, count.toString(), count.toFloat() / max) }
            }
        }
        if (stats.topTime.isNotEmpty()) {
            Group(tr("Najdłużej")) {
                val max = stats.topTime.first().second.coerceAtLeast(1)
                stats.topTime.forEach { (name, t) -> BarRow(name, PhoneText.durationShort(t, h, min, sec), t.toFloat() / max) }
            }
        }
        Group(tr("Dni tygodnia")) {
            val names = DateFormatSymbols(com.blacksingularities.opencall.i18n.I18n.locale).shortWeekdays
            // shortWeekdays: [1] = niedziela … [7] = sobota; stats: 0 = poniedziałek.
            val labels = (0 until 7).map { names[(it + 1) % 7 + 1] }
            Columns(stats.byWeekday.toList(), labels)
        }
        Group(tr("Pora dnia")) {
            val buckets = (0 until 8).map { b -> (0 until 3).sumOf { stats.byHour[b * 3 + it] } }
            Columns(buckets, (0 until 8).map { "${it * 3}" })
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color = Palette.Fg) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Fg.copy(alpha = 0.06f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, fontSize = 11.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = tint, maxLines = 1)
    }
}

@Composable
private fun BarRow(name: String, value: String, fraction: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row {
            Text(name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 13.sp, color = Palette.TextDim, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Palette.Fg.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Palette.Fg))
        }
    }
}

/** Prosty wykres słupkowy: wartości i podpisy pod słupkami. */
@Composable
private fun Columns(values: List<Int>, labels: List<String>) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(v.toString(), fontSize = 9.sp, color = Palette.TextDim)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((70f * v / max).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (v == max) Palette.Fg else Palette.Fg.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(labels.getOrElse(i) { "" }, fontSize = 10.sp, color = Palette.TextDim, maxLines = 1)
            }
        }
    }
}

/** Duplikaty: grupy kontaktów z tym samym numerem lub nazwą i scalanie ich w jeden. */
@Composable
fun DuplicatesScreen(vm: AppViewModel) {
    val groups = remember(vm.contacts) { vm.contactsRepo.duplicates(vm.contacts) }
    var preview by remember { mutableStateOf<List<Contact>?>(null) }
    Page(tr("Duplikaty"), onBack = { vm.pop() }) {
        if (groups.isEmpty()) {
            EmptyState(Icons.Rounded.Contacts, tr("Brak duplikatów"), tr("Nie znaleziono kontaktów z tym samym numerem ani nazwą."))
            return@Page
        }
        Text(tr("Scalenie łączy kontakty w jeden widok (jak w systemie) – żadne dane nie są usuwane."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        groups.forEach { g ->
            Group(g.reason) {
                g.contacts.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, c.photoUri, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.numbers.joinToString(", ") { PhoneText.pretty(it.number) }, fontSize = 11.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(Modifier.padding(10.dp)) {
                    PillButton(tr("Scal {0} kontakty", g.contacts.size), icon = Icons.Rounded.Merge) { preview = g.contacts }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    preview?.let { list ->
        MergePreviewDialog(vm, list, onDismiss = { preview = null }) {
            preview = null
            vm.mergeContacts(list.map { it.id })
        }
    }
}

/**
 * Podgląd scalenia: jak będzie wyglądał kontakt po połączeniu wpisów – wszystkie numery, e-maile,
 * adresy, daty i konta z obu stron (bez powtórzeń). Nic nie jest usuwane, więc to tylko podgląd.
 */
@Composable
private fun MergePreviewDialog(vm: AppViewModel, contacts: List<Contact>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var details by remember(contacts) { mutableStateOf<List<com.blacksingularities.opencall.data.ContactDetails>?>(null) }
    androidx.compose.runtime.LaunchedEffect(contacts) {
        details = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            contacts.mapNotNull { runCatching { vm.contactsRepo.details(it.id) }.getOrNull() }
        }
    }
    DarkDialog(
        tr("Podgląd scalenia"),
        onDismiss = onDismiss,
        buttons = {
            PillButton(tr("Anuluj"), primary = false, onClick = onDismiss)
            PillButton(tr("Scal"), icon = Icons.Rounded.Merge, enabled = details != null, onClick = onConfirm)
        },
    ) {
        val list = details
        if (list == null) {
            Text(tr("Wczytywanie…"), color = Palette.TextDim, fontSize = 13.sp)
            return@DarkDialog
        }
        val names = list.map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        val main = list.maxByOrNull { it.phones.size + it.emails.size + it.addresses.size + (if (it.photoUri != null) 2 else 0) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(main?.name, list.firstNotNullOfOrNull { it.photoUri }, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(main?.name ?: names.firstOrNull().orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                val other = names.filter { it != main?.name?.trim() }
                if (other.isNotEmpty()) Text(tr("Także jako: {0}", other.joinToString(", ")), fontSize = 12.sp, color = Palette.TextDim)
            }
        }
        val phones = ArrayList<String>()
        list.flatMap { it.phones }.forEach { p -> if (phones.none { PhoneText.sameNumber(it, p.number) }) phones.add(p.number) }
        MergeSection(tr("Numery"), phones.map { PhoneText.pretty(it) })
        MergeSection(tr("E-mail"), list.flatMap { d -> d.emails.map { it.value } }.distinctBy { it.lowercase() })
        MergeSection(tr("Adres"), list.flatMap { d -> d.addresses.map { it.value } }.distinctBy { it.lowercase() })
        MergeSection(tr("Praca"), list.mapNotNull { d -> listOfNotNull(d.title, d.organization).joinToString(" · ").ifBlank { null } }.distinct())
        MergeSection(
            tr("Daty"),
            (list.mapNotNull { d -> d.birthday?.let { tr("Urodziny") + ": " + com.blacksingularities.opencall.ui.Format.eventDate(it) } } +
                list.flatMap { d -> d.events.map { com.blacksingularities.opencall.ui.Format.eventType(it.type, it.label) + ": " + com.blacksingularities.opencall.ui.Format.eventDate(it.value) } }).distinct(),
        )
        MergeSection(tr("Strony WWW"), list.flatMap { d -> d.websites.map { it.value } }.distinct())
        MergeSection(tr("Notatka"), list.mapNotNull { it.note }.distinct())
        MergeSection(tr("Konta"), list.flatMap { d -> d.accountRefs.map { accountBadge(it) } }.distinct())
        Text(tr("Wpisy zostaną połączone w jeden kontakt (jak w systemie). Żadne dane nie są usuwane – w szczegółach kontaktu można je później rozłączyć."), color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun MergeSection(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Column {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.TextDim)
        values.forEach { Text(it, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
    }
}

/** Szybkie wybieranie: klawisze 2–9 przytrzymane na klawiaturze dzwonią pod przypisany numer. */
@Composable
fun SpeedDialScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var pickContact by remember { mutableStateOf<Int?>(null) }
    var input by remember { mutableStateOf("") }
    Page(tr("Szybkie wybieranie"), onBack = { vm.pop() }) {
        Text(tr("Przytrzymaj cyfrę na pustej klawiaturze, aby od razu zadzwonić. „1” to poczta głosowa."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        Group(null) {
            (2..9).forEach { k ->
                val n = vm.settings.speedDial[k]
                val c = n?.let { vm.contactFor(it) }
                ActionRow(
                    Icons.Rounded.Person,
                    "$k · " + (c?.name ?: n?.let { PhoneText.pretty(it) } ?: tr("Nie przypisano")),
                    if (c != null) PhoneText.pretty(n) else null,
                    trailing = {
                        if (n != null) RoundIcon(Icons.Rounded.Close, tr("Usuń przypisanie"), size = 34.dp) { vm.updateSettings { it.copy(speedDial = it.speedDial - k) } }
                    },
                ) {
                    input = n ?: ""
                    editing = k
                }
            }
        }
    }
    editing?.let { k ->
        DarkDialog(
            tr("Klawisz {0}", k),
            onDismiss = { editing = null },
            buttons = {
                PillButton(tr("Z kontaktów"), primary = false) {
                    editing = null
                    pickContact = k
                }
                PillButton(tr("Zapisz"), enabled = input.any { it.isDigit() }) {
                    vm.updateSettings { it.copy(speedDial = it.speedDial + (k to input.trim())) }
                    editing = null
                }
            },
        ) {
            DarkField(input, { input = it }, tr("Numer telefonu"), keyboard = KeyboardType.Phone)
        }
    }
    pickContact?.let { k ->
        val list = remember(vm.contacts) { vm.contacts.filter { it.numbers.isNotEmpty() } }
        ChoiceDialog(tr("Wybierz kontakt"), list.take(300).map { Icons.Rounded.Person to it.name + " · " + PhoneText.pretty(it.primaryNumber!!.number) }, onDismiss = { pickContact = null }) { i ->
            pickContact = null
            vm.updateSettings { it.copy(speedDial = it.speedDial + (k to list[i].primaryNumber!!.number)) }
        }
    }
}

/** Szybkie odpowiedzi SMS przy odrzucaniu połączenia. */
@Composable
fun QuickRepliesScreen(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var input by remember { mutableStateOf("") }
    val replies = vm.settings.quickReplies
    Page(tr("Szybkie odpowiedzi"), onBack = { vm.pop() }, actions = {
        RoundIcon(Icons.Rounded.RestartAlt, tr("Przywróć domyślne")) { vm.updateSettings { it.copy(quickReplies = com.blacksingularities.opencall.data.AppSettings.DEFAULT_REPLIES) } }
        RoundIcon(Icons.Rounded.Add, tr("Dodaj odpowiedź"), enabled = replies.size < 8) {
            input = ""
            editing = -1
        }
    }) {
        Group(null) {
            replies.forEachIndexed { i, r ->
                ActionRow(
                    Icons.Rounded.Sms,
                    tr(r),
                    trailing = {
                        RoundIcon(Icons.Rounded.Delete, tr("Usuń"), size = 34.dp, enabled = replies.size > 1) {
                            vm.updateSettings { it.copy(quickReplies = it.quickReplies.filterIndexed { j, _ -> j != i }) }
                        }
                    },
                ) {
                    input = tr(r)
                    editing = i
                }
            }
        }
        Text(tr("Wybierasz je na ekranie dzwonienia: „Odrzuć z wiadomością”."), color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
    }
    editing?.let { i ->
        DarkDialog(
            if (i < 0) tr("Nowa odpowiedź") else tr("Edytuj odpowiedź"),
            onDismiss = { editing = null },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { editing = null }
                PillButton(tr("Zapisz"), icon = Icons.Rounded.Edit, enabled = input.isNotBlank()) {
                    val text = input.trim()
                    vm.updateSettings { s ->
                        s.copy(quickReplies = if (i < 0) s.quickReplies + text else s.quickReplies.toMutableList().also { it[i] = text })
                    }
                    editing = null
                }
            },
        ) {
            DarkField(input, { input = it.take(160) }, tr("Treść wiadomości"), singleLine = false, minLines = 2)
            Text("${input.length}/160", color = Palette.TextFaint, fontSize = 11.sp)
        }
    }
}

/** Kopia zapasowa: kontakty jako vCard (eksport i import), historia połączeń jako CSV. */
@Composable
fun BackupScreen(vm: AppViewModel) {
    val exportVcf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri -> uri?.let { vm.exportContacts(it) } }
    val importVcf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importContacts(it) } }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCallLog(it) } }
    val stamp = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date()) }
    Page(tr("Kopia zapasowa"), onBack = { vm.pop() }) {
        Group(tr("Kontakty")) {
            ActionRow(Icons.Rounded.Upload, tr("Eksportuj do pliku vCard"), tr("{0} kontaktów · .vcf (Google, iPhone, Outlook)", vm.contacts.size), enabled = vm.contacts.isNotEmpty()) {
                exportVcf.launch("Kontakty-$stamp.vcf")
            }
            ActionRow(Icons.Rounded.Download, tr("Importuj z pliku vCard"), tr("Pomija kontakty, które już masz")) {
                importVcf.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream", "*/*"))
            }
        }
        Group(tr("Historia połączeń")) {
            ActionRow(Icons.Rounded.TableChart, tr("Eksportuj do CSV"), tr("{0} wpisów · otworzysz w Excelu lub Arkuszach", vm.calls.size), enabled = vm.calls.isNotEmpty()) {
                exportCsv.launch("Polaczenia-$stamp.csv")
            }
        }
        Text(
            tr("Pliki zapisujesz, gdzie chcesz – w telefonie, na karcie albo w chmurze. Nic nie jest wysyłane automatycznie."),
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}
