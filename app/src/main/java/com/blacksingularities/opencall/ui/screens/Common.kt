package com.blacksingularities.opencall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.UpdateBanner
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.glass
import com.blacksingularities.opencall.ui.theme.reactive

/** Przyciski po prawej stronie nagłówka – tej samej wielkości co zakładki-chipy obok. */
internal val HeaderButtonSize = 30.dp
internal val HeaderIconSize = 18.dp

/**
 * Nagłówek zakładki bez tytułu: tylko akcje i ustawienia przy prawej krawędzi (z plakietką,
 * gdy czeka aktualizacja). Nazwę zakładki widać na dolnym pasku, więc nie zajmuje tu miejsca.
 */
@Composable
fun TabHeader(vm: AppViewModel, leading: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Box(Modifier.weight(1f)) { leading?.invoke() }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
                MoreMenu(vm)
            }
        }
        UpdateBanner(vm, Modifier.padding(top = 10.dp, end = 6.dp))
    }
}

/**
 * Nagłówek zakładki w stylu OpenSMS: po lewej [leading] (np. filtry), a obok lupa, która po dotknięciu
 * płynnie rozciąga się w lewo w pole wyszukiwania i zakrywa [leading]. Stan trzyma ekran, bo od
 * [query] zależy jego lista; do czasu wpisania czegokolwiek lista się nie zmienia.
 */
@Composable
fun SearchHeader(
    vm: AppViewModel,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    hint: String,
    leading: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val close = {
        onSearching(false)
        onQuery("")
    }
    BackHandler(enabled = searching, onBack = close)
    val p by animateFloatAsState(if (searching) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "searchGrow")
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (p < 1f) {
                    Box(Modifier.fillMaxWidth().padding(end = HeaderButtonSize + 8.dp).graphicsLayer { alpha = 1f - p }) { leading() }
                }
                SearchPill(searching, p, query, onQuery, hint, { onSearching(true) }, close, Modifier.width(lerp(HeaderButtonSize, maxWidth, p)))
            }
            actions()
            MoreMenu(vm)
        }
        UpdateBanner(vm, Modifier.padding(top = 10.dp, end = 6.dp))
    }
}

/** Lupa albo – w trakcie szukania – pole ze strzałką wstecz i czyszczeniem. */
@Composable
private fun SearchPill(
    searching: Boolean,
    p: Float,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    Row(
        // Złożona lupa ma rozmiar zakładek obok; rozwinięte pole jest wyższe, wygodne do pisania.
        modifier.height(lerp(HeaderButtonSize, 40.dp, p)).glass(RoundedCornerShape(50)).clip(RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (searching) tr("Zamknij wyszukiwanie") else tr("Szukaj")
        Box(Modifier.size(lerp(HeaderButtonSize, 40.dp, p)).reactive(label, onClick = if (searching) onClose else onOpen), contentAlignment = Alignment.Center) {
            Icon(if (searching) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Search, null, tint = Palette.Fg, modifier = Modifier.size(HeaderIconSize))
        }
        if (searching || p > 0f) {
            Box(Modifier.weight(1f).graphicsLayer { alpha = p }) {
                if (value.isEmpty()) Text(hint, color = Palette.TextFaint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    enabled = searching,
                    textStyle = TextStyle(color = Palette.Fg, fontSize = 15.sp),
                    cursorBrush = SolidColor(Palette.Fg),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (value.isNotEmpty()) {
                Box(Modifier.size(44.dp).reactive(tr("Wyczyść")) { onChange("") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Palette.Fg, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

/**
 * Karta konfiguracji: rola domyślnej aplikacji telefonu (ekran rozmowy, blokowanie, rejestr)
 * oraz brakujące uprawnienia. Znika, gdy wszystko jest gotowe.
 */
@Composable
fun SetupCard(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    val needRole = !vm.isDefaultDialer
    val needPerms = vm.missingPermissions.isNotEmpty()
    if (!needRole && !needPerms) return
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.Fg)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (needRole) Icons.AutoMirrored.Rounded.PhoneForwarded else Icons.Rounded.Lock, null, tint = Palette.Ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                if (needRole) tr("Ustaw OpenDial jako aplikację telefonu") else tr("Zezwól na dostęp"),
                color = Palette.Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (needRole) {
                tr("Wtedy OpenDial obsługuje ekran rozmowy, odbieranie, blokowanie numerów i powiadomienia o nieodebranych.")
            } else {
                tr("Kontakty, historia połączeń i telefon są potrzebne, żeby wyświetlać listy i dzwonić.")
            },
            color = Palette.Ink.copy(alpha = 0.65f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        PillButtonDark(if (needRole) tr("Ustaw jako domyślną") else tr("Zezwól")) {
            if (needRole) onRequestRole() else onRequestPermissions()
        }
    }
}

@Composable
private fun PillButtonDark(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Palette.Fg,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .reactive(text, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(Palette.Ink)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

/** Komunikat o braku uprawnienia w miejscu listy. */
@Composable
fun NoPermission(text: String, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = Palette.TextDim, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = Palette.TextDim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        PillButton(tr("Zezwól"), onClick = onRequest)
    }
}

/** Nagłówek grupy na liście („DZISIAJ”, „A”). */
@Composable
fun ListHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Palette.Amber,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}


/** Przycisk „⋮” z menu: ustawienia i najczęstsze narzędzia. */
@Composable
fun MoreMenu(vm: AppViewModel) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        RoundIcon(androidx.compose.material.icons.Icons.Rounded.MoreVert, tr("Więcej"), badge = if (vm.updatePending) "1" else null, size = HeaderButtonSize, iconSize = HeaderIconSize) { open = true }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = Palette.PanelHigh,
        ) {
            @Composable
            fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, route: Route, dot: Boolean = false) = androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, color = Palette.Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                leadingIcon = { Icon(icon, null, tint = Palette.TextFaint, modifier = Modifier.size(20.dp)) },
                trailingIcon = if (dot) ({ Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Palette.Record)) }) else null,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(44.dp),
                onClick = { open = false; vm.push(route) },
            )
            if (!vm.settings.showFavoritesTab) item(androidx.compose.material.icons.Icons.Rounded.Star, tr("Ulubione"), Route.Favorites)
            item(androidx.compose.material.icons.Icons.Rounded.Block, tr("Zablokowane numery i filtry"), Route.Blocked)
            item(androidx.compose.material.icons.Icons.Rounded.BarChart, tr("Statystyki"), Route.Stats)
            item(Icons.Rounded.Settings, tr("Ustawienia"), Route.Settings, dot = vm.updatePending)
        }
    }
}

/** Kolejność zakładek głównego ekranu – ta sama dla chipów u góry i przesuwania palcem w bok. */
fun mainPages(vm: AppViewModel): List<Pair<Int, Int>> = buildList {
    if (vm.settings.showFavoritesTab) add(0 to 0)
    for (f in 0..4) add(1 to f)
    add(2 to 0)
}

fun AppViewModel.goPage(page: Pair<Int, Int>) {
    if (page.first == 1) recentFilter = page.second
    if (tab != page.first) selectTab(page.first)
}

/** Przesuwa o [delta] zakładek (przesunięcie palcem w bok). */
fun AppViewModel.stepPage(delta: Int) {
    val pages = mainPages(this)
    val cur = pages.indexOf(if (tab == 1) 1 to recentFilter else tab to 0).coerceAtLeast(0)
    if (pages.isEmpty()) return
    // W kółko: z pierwszej zakładki w lewo – ostatnia, z ostatniej w prawo – pierwsza.
    goPage(pages[(cur + delta).mod(pages.size)])
}

/**
 * Górny pasek zakładek: ulubione, filtry historii i kontakty. Wybrana zakładka jest zawsze widoczna
 * w całości – pasek sam przewija się do niej (przy wejściu na ekran od razu, potem płynnie).
 */
@Composable
fun NavChips(vm: AppViewModel) {
    val icons = listOf(
        androidx.compose.material.icons.Icons.Rounded.History to tr("Wszystkie"),
        androidx.compose.material.icons.Icons.AutoMirrored.Rounded.CallMissed to tr("Nieodebrane"),
        androidx.compose.material.icons.Icons.AutoMirrored.Rounded.CallReceived to tr("Przychodzące"),
        androidx.compose.material.icons.Icons.AutoMirrored.Rounded.CallMade to tr("Wychodzące"),
        androidx.compose.material.icons.Icons.Rounded.Block to tr("Zablokowane"),
    )
    val pages = mainPages(vm)
    val selectedIndex = pages.indexOfFirst { page -> vm.tab == page.first && (page.first != 1 || vm.recentFilter == page.second) }
    val scroll = androidx.compose.foundation.rememberScrollState()
    val bounds = remember { androidx.compose.runtime.mutableStateMapOf<Int, IntRange>() }
    var viewport by remember { mutableStateOf(0) }
    val firstMove = remember { booleanArrayOf(true) }
    val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 16.dp.roundToPx() }
    val sel = bounds[selectedIndex]
    LaunchedEffect(selectedIndex, sel, viewport, scroll.maxValue) {
        if (sel == null || viewport == 0) return@LaunchedEffect
        val target = when {
            sel.first - margin < scroll.value -> sel.first - margin
            sel.last + margin > scroll.value + viewport -> sel.last + margin - viewport
            else -> return@LaunchedEffect
        }.coerceIn(0, scroll.maxValue)
        if (firstMove[0]) scroll.scrollTo(target) else scroll.animateScrollTo(target)
        firstMove[0] = false
    }
    Row(
        Modifier
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEachIndexed { i, page ->
            val (icon, label) = when (page.first) {
                0 -> androidx.compose.material.icons.Icons.Rounded.Star to tr("Ulubione")
                2 -> androidx.compose.material.icons.Icons.Rounded.Contacts to tr("Kontakty")
                else -> icons[page.second]
            }
            val badge = if (page == (1 to 1) && vm.unseenMissed > 0) vm.unseenMissed else null
            com.blacksingularities.opencall.ui.components.TabChip(
                icon, label, i == selectedIndex,
                modifier = Modifier.onPlaced { c ->
                    val x = c.positionInParent().x.toInt()
                    bounds[i] = x..(x + c.size.width)
                },
                count = badge,
                compact = true,
            ) { vm.goPage(page) }
        }
    }
}
