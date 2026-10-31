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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
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
        BottomBar(vm)
    }
}

@Composable
private fun BottomBar(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (vm.settings.showFavoritesTab) TabItem(Icons.Rounded.Star, tr("Ulubione"), vm.tab == 0) { vm.selectTab(0) }
        // „Telefon” = historia i klawiatura w jednym widoku; ponowne dotknięcie rozwija/zwija klawiaturę.
        TabItem(Icons.Rounded.Call, tr("Telefon"), vm.tab == 1, badge = vm.unseenMissed) {
            if (vm.tab == 1) vm.keypadOpen = !vm.keypadOpen else vm.selectTab(1)
        }
        TabItem(Icons.Rounded.Contacts, tr("Kontakty"), vm.tab == 2) { vm.selectTab(2) }
    }
}

/** Zakładka dolnego paska: sama ikona, a wybrana – pigułka z ikoną i nazwą. */
@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    // Styl wspólny dla aplikacji Open: wybrana zakładka to biały kafel z czarną ikoną i podpisem.
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "tabBg")
    val tint by animateColorAsState(if (selected) Color.Black else Palette.TextDim, label = "tabTint")
    Box(Modifier.weight(1f)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .reactive(label, onClick = onClick)
                .padding(top = 7.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = tint,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
        }
        if (badge > 0) {
            Text(
                if (badge > 99) "99+" else badge.toString(),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 26.dp, top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Palette.Record)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
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
                        .background(Color.White.copy(alpha = 0.07f))
                        .reactive(s.label, pressScale = 0.97f) { vm.confirmPending(s.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
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
