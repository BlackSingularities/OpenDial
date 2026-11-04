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
import androidx.compose.runtime.remember
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

/**
 * Nagłówek zakładki bez tytułu: tylko akcje i ustawienia przy prawej krawędzi (z plakietką,
 * gdy czeka aktualizacja). Nazwę zakładki widać na dolnym pasku, więc nie zajmuje tu miejsca.
 */
@Composable
fun TabHeader(vm: AppViewModel, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
                RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), badge = if (vm.updatePending) "1" else null) { vm.push(Route.Settings) }
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
                    Box(Modifier.fillMaxWidth().padding(end = 50.dp).graphicsLayer { alpha = 1f - p }) { leading() }
                }
                SearchPill(searching, p, query, onQuery, hint, { onSearching(true) }, close, Modifier.width(lerp(44.dp, maxWidth, p)))
            }
            actions()
            RoundIcon(Icons.Rounded.Settings, tr("Ustawienia"), badge = if (vm.updatePending) "1" else null) { vm.push(Route.Settings) }
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
        modifier.height(44.dp).glass(RoundedCornerShape(50)).clip(RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (searching) tr("Zamknij wyszukiwanie") else tr("Szukaj")
        Box(Modifier.size(44.dp).reactive(label, onClick = if (searching) onClose else onOpen), contentAlignment = Alignment.Center) {
            Icon(if (searching) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        if (searching || p > 0f) {
            Box(Modifier.weight(1f).graphicsLayer { alpha = p }) {
                if (value.isEmpty()) Text(hint, color = Palette.TextFaint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    enabled = searching,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (value.isNotEmpty()) {
                Box(Modifier.size(44.dp).reactive(tr("Wyczyść")) { onChange("") }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
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
            .background(Color.White)
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
        color = Color.White,
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
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
    )
}
