package com.blacksingularities.opencall.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.blacksingularities.opencall.ui.screens.stepPage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.call.CallActivity
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.MessagePill
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.screens.AiHistoryScreen
import com.blacksingularities.opencall.ui.screens.AiScreeningScreen
import com.blacksingularities.opencall.ui.screens.BackupScreen
import com.blacksingularities.opencall.ui.screens.BlockedScreen
import com.blacksingularities.opencall.ui.screens.ContactInfoScreen
import com.blacksingularities.opencall.ui.screens.ContactsTab
import com.blacksingularities.opencall.ui.screens.DuplicatesScreen
import com.blacksingularities.opencall.ui.screens.EditContactScreen
import com.blacksingularities.opencall.ui.screens.FavoritesTab
import com.blacksingularities.opencall.ui.screens.QuickRepliesScreen
import com.blacksingularities.opencall.ui.screens.RecentsTab
import com.blacksingularities.opencall.ui.screens.SettingsScreen
import com.blacksingularities.opencall.ui.screens.SpeedDialScreen
import com.blacksingularities.opencall.ui.screens.StatsScreen
import com.blacksingularities.opencall.ui.theme.GlassScope
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

@Composable
fun AppRoot(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit) {
    GlassScope(haptics = vm.settings.haptics) {
        BackHandler(enabled = vm.stack.isNotEmpty()) { vm.pop() }
        Column(Modifier.fillMaxSize()) {
            val inCall = OngoingCallBar()
            // Pasek rozmowy zajmuje już miejsce paska stanu – ekrany niżej go nie dublują.
            Box(Modifier.weight(1f).then(if (inCall) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
                AnimatedContent(
                    targetState = vm.route,
                    transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 8 } + fadeOut()) },
                    label = "route",
                ) { r ->
                    when (r) {
                        null -> MainTabs(vm, onRequestRole, onRequestPermissions)
                        is Route.ContactInfo -> ContactInfoScreen(vm, r.id, null)
                        is Route.NumberInfo -> ContactInfoScreen(vm, null, r.number)
                        is Route.Edit -> EditContactScreen(vm, r.draft)
                        Route.Settings -> SettingsScreen(vm, onRequestRole)
                        Route.Blocked -> BlockedScreen(vm)
                        Route.AiScreening -> AiScreeningScreen(vm)
                        Route.AiHistory -> AiHistoryScreen(vm)
                        Route.Stats -> StatsScreen(vm)
                        Route.Duplicates -> DuplicatesScreen(vm)
                        Route.SpeedDial -> SpeedDialScreen(vm)
                        Route.QuickReplies -> QuickRepliesScreen(vm)
                        Route.Backup -> BackupScreen(vm)
                        Route.Favorites -> FavoritesTab(vm, onRequestRole, onRequestPermissions, onBack = { vm.pop() })
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
            MessagePill(vm.message)
        }
        PendingCallDialog(vm)
        InfoDialog(vm)
    }
}

/** Pasek „Trwa rozmowa” nad aplikacją – powrót do ekranu rozmowy jednym dotknięciem. */
@Composable
private fun OngoingCallBar(): Boolean {
    val context = LocalContext.current
    val tick by CallManager.tick.collectAsState()
    val primary = remember(tick) { CallManager.primary() } ?: return false
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Answer)
            .windowInsetsPadding(WindowInsets.statusBars)
            .reactive(tr("Wróć do rozmowy"), pressScale = 0.98f) { context.startActivity(CallActivity.intent(context)) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PhoneInTalk, null, tint = Palette.Ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            tr("Trwa rozmowa: {0} · dotknij, aby wrócić", primary.title.ifBlank { tr("Numer prywatny") }),
            color = Palette.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
    return true
}

@Composable
private fun MainTabs(vm: AppViewModel, onRequestRole: () -> Unit, onRequestPermissions: () -> Unit) {
    // Przesunięcie palcem w bok zmienia zakładkę (ulubione → filtry historii → kontakty).
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                var acc = 0f
                detectHorizontalDragGestures(
                    onDragStart = { acc = 0f },
                    onDragEnd = {
                        if (acc <= -threshold) vm.stepPage(1) else if (acc >= threshold) vm.stepPage(-1)
                        acc = 0f
                    },
                ) { _, dx -> acc += dx }
            },
    ) {
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> FavoritesTab(vm, onRequestRole, onRequestPermissions)
                2 -> ContactsTab(vm, onRequestRole, onRequestPermissions)
                else -> RecentsTab(vm, onRequestRole, onRequestPermissions)
            }
        }
    }
}

/** Okno z informacją do odczytania lub skopiowania (np. IMEI po „*#06#”). */
@Composable
private fun InfoDialog(vm: AppViewModel) {
    val (title, text) = vm.infoDialog ?: return
    DarkDialog(
        title,
        onDismiss = { vm.infoDialog = null },
        buttons = { PillButton(tr("Zamknij"), primary = false) { vm.infoDialog = null } },
    ) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
    }
}

/** Wybór karty SIM i/lub potwierdzenie połączenia. */
@Composable
private fun PendingCallDialog(vm: AppViewModel) {
    val p = vm.pendingCall ?: return
    val title = p.name ?: PhoneText.pretty(p.number)
    DarkDialog(
        if (p.needsSim) tr("Zadzwoń z karty") else tr("Zadzwonić?"),
        onDismiss = { vm.pendingCall = null },
        buttons = {
            PillButton(tr("Anuluj"), primary = false) { vm.pendingCall = null }
            if (!p.needsSim) PillButton(tr("Zadzwoń"), icon = Icons.Rounded.Call) { vm.confirmPending(null) }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, vm.contactFor(p.number)?.photoUri, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (p.name != null) Text(PhoneText.pretty(p.number), fontSize = 13.sp, color = Palette.TextDim)
            }
        }
        if (p.needsSim) {
            vm.sims.forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Fg.copy(alpha = 0.07f))
                        .reactive(s.label, pressScale = 0.97f) { vm.confirmPending(s.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.Fg), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SimCard, null, tint = Palette.Ink, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(s.number ?: tr("SIM {0}", s.slot + 1), fontSize = 12.sp, color = Palette.TextDim)
                    }
                }
            }
            Text(tr("Domyślną kartę ustawisz w Ustawieniach → Połączenia."), fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}
