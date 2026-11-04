package com.blacksingularities.opencall.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.BuildConfig
import com.blacksingularities.opencall.call.Dialer
import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.NameOrder
import com.blacksingularities.opencall.data.RecentTap
import com.blacksingularities.opencall.data.StartTab
import com.blacksingularities.opencall.i18n.AppLanguage
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.CompactChoice
import com.blacksingularities.opencall.ui.components.Group
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.SectionHeader
import com.blacksingularities.opencall.ui.components.SwitchRow
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.update.UpdateManager
import com.blacksingularities.opencall.update.UpdateState

@Composable
fun SettingsScreen(vm: AppViewModel, onRequestRole: () -> Unit) {
    val s = vm.settings
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun set(t: (AppSettings) -> AppSettings) = vm.updateSettings(t)
    var simPicker by remember { mutableStateOf(false) }
    var startPicker by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Page(tr("Ustawienia"), onBack = { vm.pop() }) {
        if (!vm.isDefaultDialer) {
            Group(null) {
                ActionRow(Icons.AutoMirrored.Rounded.PhoneForwarded, tr("Ustaw jako domyślną aplikację telefonu"), tr("Ekran rozmowy, blokowanie i powiadomienia o nieodebranych"), onClick = onRequestRole)
            }
        }

        SectionHeader(Icons.Rounded.Call, tr("Połączenia"))
        Group(null) {
            if (vm.sims.size > 1) {
                ActionRow(
                    Icons.Rounded.SimCard,
                    tr("Domyślna karta SIM"),
                    vm.sims.firstOrNull { it.id == s.defaultSim }?.label ?: tr("Pytaj za każdym razem"),
                ) { simPicker = true }
            }
            SwitchRow(Icons.Rounded.TouchApp, tr("Potwierdzaj połączenia"), tr("Okno „Zadzwonić?” przed wybraniem numeru – chroni przed przypadkowym dotknięciem"), s.confirmCall) { v -> set { it.copy(confirmCall = v) } }
            ActionRow(Icons.Rounded.Speed, tr("Szybkie wybieranie"), tr("{0} z 8 klawiszy przypisanych", s.speedDial.size)) { vm.push(Route.SpeedDial) }
            ActionRow(Icons.Rounded.Sms, tr("Szybkie odpowiedzi"), tr("Wiadomości przy odrzucaniu połączenia")) { vm.push(Route.QuickReplies) }
            ActionRow(Icons.Rounded.Voicemail, tr("Poczta głosowa"), Dialer.voicemailNumber(vm.getApplication()) ?: tr("Przytrzymaj „1” na klawiaturze")) { vm.voicemail() }
            ActionRow(Icons.Rounded.PhoneInTalk, tr("Ustawienia połączeń operatora"), tr("Przekierowania, połączenia oczekujące, ID dzwoniącego")) {
                vm.openIntent(Intent(android.telecom.TelecomManager.ACTION_SHOW_CALL_SETTINGS))
            }
        }

        SectionHeader(Icons.Rounded.SmartToy, tr("Asystent AI"))
        Group(null) {
            ActionRow(
                Icons.Rounded.SmartToy,
                tr("Kto dzwoni naprawdę"),
                if (s.ai.ready) tr("Włączony: AI odbiera nieznane numery") else tr("AI odbiera nieznane numery i pyta o cel"),
            ) { vm.push(Route.AiScreening) }
        }

        SectionHeader(Icons.Rounded.PhoneInTalk, tr("W trakcie rozmowy"))
        Group(null) {
            SwitchRow(Icons.Rounded.Swipe, tr("Odbieranie przesunięciem"), tr("Przesuń gałkę w prawo, aby odebrać – bez przypadkowych odebrań w kieszeni"), s.swipeToAnswer) { v -> set { it.copy(swipeToAnswer = v) } }
            SwitchRow(Icons.Rounded.Sensors, tr("Czujnik zbliżeniowy"), tr("Wygaszaj ekran przy uchu"), s.proximity) { v -> set { it.copy(proximity = v) } }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracja po połączeniu"), tr("Krótka wibracja, gdy rozmówca odbierze"), s.vibrateOnConnect) { v -> set { it.copy(vibrateOnConnect = v) } }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracja po zakończeniu"), tr("Gdy rozmowa się skończy"), s.vibrateOnEnd) { v -> set { it.copy(vibrateOnEnd = v) } }
        }

        SectionHeader(Icons.Rounded.Dialpad, tr("Klawiatura"))
        Group(null) {
            SwitchRow(Icons.AutoMirrored.Rounded.VolumeUp, tr("Dźwięki klawiszy"), tr("Tony DTMF przy wpisywaniu numeru"), s.dtmfTones) { v -> set { it.copy(dtmfTones = v) } }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracja klawiszy"), null, s.keypadVibrate) { v -> set { it.copy(keypadVibrate = v) } }
            SwitchRow(Icons.Rounded.GraphicEq, tr("Wyszukiwanie T9"), tr("Cyfry podpowiadają kontakty po nazwie: 5-2-6 → Jan"), s.t9Search) { v -> set { it.copy(t9Search = v) } }
        }

        SectionHeader(Icons.Rounded.History, tr("Historia i kontakty"))
        Group(null) {
            SwitchRow(Icons.AutoMirrored.Rounded.MergeType, tr("Grupuj połączenia"), tr("Kolejne połączenia z tym samym numerem tego dnia w jednym wierszu"), s.groupCalls) { v -> set { it.copy(groupCalls = v) } }
            CompactChoice(
                Icons.Rounded.TouchApp,
                tr("Dotknięcie wpisu"),
                RecentTap.entries,
                s.recentTap,
                { when (it) { RecentTap.EXPAND -> tr("Rozwija szczegóły"); RecentTap.CALL -> tr("Dzwoni"); RecentTap.DETAILS -> tr("Otwiera kontakt") } },
                short = { when (it) { RecentTap.EXPAND -> tr("Rozwiń"); RecentTap.CALL -> tr("Dzwoń"); RecentTap.DETAILS -> tr("Kontakt") } },
            ) { v -> set { it.copy(recentTap = v) } }
            if (vm.sims.size > 1) SwitchRow(Icons.Rounded.SimCard, tr("Pokazuj kartę SIM"), null, s.showSim) { v -> set { it.copy(showSim = v) } }
            SwitchRow(Icons.Rounded.Place, tr("Lokalizacja numeru"), tr("Region dla numerów spoza kontaktów"), s.showCallerLocation) { v -> set { it.copy(showCallerLocation = v) } }
            CompactChoice(
                Icons.AutoMirrored.Rounded.Sort,
                tr("Kolejność nazw"),
                NameOrder.entries,
                s.nameOrder,
                { if (it == NameOrder.FIRST_LAST) tr("Imię Nazwisko") else tr("Nazwisko, Imię") },
                short = { if (it == NameOrder.FIRST_LAST) tr("Imię") else tr("Nazwisko") },
            ) { v -> set { it.copy(nameOrder = v) } }
            SwitchRow(Icons.Rounded.Contacts, tr("Tylko z numerem"), tr("Ukryj kontakty bez numeru telefonu"), s.onlyWithNumbers) { v -> set { it.copy(onlyWithNumbers = v) } }
            SwitchRow(Icons.Rounded.Star, tr("Zakładka „Ulubione”"), tr("Pokazuj ulubione na dolnym pasku"), s.showFavoritesTab) { v ->
                set { it.copy(showFavoritesTab = v, startTab = if (!v && it.startTab == StartTab.FAVORITES) StartTab.RECENTS else it.startTab) }
            }
            if (s.showFavoritesTab) {
                SwitchRow(Icons.Rounded.Favorite, tr("Sekcja „Często”"), tr("Najczęstsi rozmówcy pod ulubionymi"), s.showFrequent) { v -> set { it.copy(showFrequent = v) } }
            } else {
                ActionRow(Icons.Rounded.Star, tr("Ulubione"), tr("Otwórz listę ulubionych kontaktów")) { vm.push(Route.Favorites) }
            }
            if (vm.calls.isNotEmpty()) {
                ActionRow(Icons.Rounded.DeleteSweep, tr("Wyczyść historię"), tr("Usuń cały rejestr połączeń z telefonu"), danger = true) { confirmClear = true }
            }
        }

        SectionHeader(Icons.Rounded.Block, tr("Blokowanie i narzędzia"))
        Group(null) {
            ActionRow(Icons.Rounded.Block, tr("Zablokowane numery i filtry"), tr("{0} numerów · {1} reguł", vm.blocked.size, s.blockRules.size)) { vm.push(Route.Blocked) }
            ActionRow(Icons.Rounded.BarChart, tr("Statystyki połączeń"), tr("Czas rozmów, najczęstsi rozmówcy, pory dnia")) { vm.push(Route.Stats) }
            ActionRow(Icons.AutoMirrored.Rounded.MergeType, tr("Duplikaty kontaktów"), tr("Znajdź i scal powtórzone kontakty")) { vm.push(Route.Duplicates) }
            ActionRow(Icons.Rounded.Save, tr("Kopia zapasowa"), tr("Eksport i import vCard, historia do CSV")) { vm.push(Route.Backup) }
        }

        SectionHeader(Icons.Rounded.Settings, tr("Ogólne"))
        Group(null) {
            CompactChoice(
                Icons.Rounded.Language,
                tr("Język aplikacji"),
                AppLanguage.entries,
                s.language,
                { when (it) { AppLanguage.SYSTEM -> tr("Systemowy"); AppLanguage.PL -> "Polski"; AppLanguage.EN -> "English" } },
                short = { when (it) { AppLanguage.SYSTEM -> "Auto"; AppLanguage.PL -> "PL"; AppLanguage.EN -> "EN" } },
            ) { v -> set { it.copy(language = v) } }
            ActionRow(Icons.Rounded.Home, tr("Ekran startowy"), startLabel(s.startTab)) { startPicker = true }
            SwitchRow(Icons.Rounded.Vibration, tr("Wibracja przycisków"), tr("Lekka wibracja przy dotknięciu i przytrzymaniu przycisków"), s.haptics) { v -> set { it.copy(haptics = v) } }
            ActionRow(Icons.Rounded.NotificationsActive, tr("Powiadomienia"), tr("Dźwięki i wygląd powiadomień o połączeniach")) {
                vm.openIntent(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, vm.getApplication<android.app.Application>().packageName))
            }
            ActionRow(Icons.AutoMirrored.Rounded.CallMissed, tr("Aplikacje domyślne"), tr("Zmień aplikację telefonu w ustawieniach systemu")) {
                vm.openIntent(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
        }

        SectionHeader(Icons.Rounded.SystemUpdate, tr("Aktualizacje"))
        Group(null) {
            SwitchRow(Icons.Rounded.SystemUpdate, tr("Sprawdzaj automatycznie"), tr("Przy uruchomieniu, najwyżej co 6 godzin – z GitHub Releases"), s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }
            val u = vm.update
            ActionRow(
                Icons.Rounded.SystemUpdate,
                when (u) {
                    is UpdateState.Available -> tr("Zainstaluj wersję {0}", u.info.version)
                    is UpdateState.Ready -> tr("Zainstaluj wersję {0}", u.info.version)
                    else -> tr("Sprawdź aktualizacje")
                },
                when (u) {
                    UpdateState.Checking -> tr("Sprawdzanie…")
                    UpdateState.UpToDate -> tr("Masz najnowszą wersję {0}", UpdateManager.currentVersion)
                    is UpdateState.Failed -> u.message
                    is UpdateState.Downloading -> tr("Pobieranie… {0}%", (u.progress * 100).toInt())
                    is UpdateState.Installing -> tr("Instalowanie…")
                    is UpdateState.Available -> tr("Nowa wersja na GitHubie")
                    else -> tr("Wersja {0}", UpdateManager.currentVersion)
                },
            ) { vm.updateNow() }
            if (u is UpdateState.Downloading) {
                LinearProgressIndicator(
                    progress = { u.progress },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }

        SectionHeader(Icons.Rounded.Info, tr("O aplikacji"))
        Group(null) {
            ActionRow(Icons.Rounded.Person, tr("Pomysł i wykonanie"), "Adam Rędzikowski · redzikowski.com") {
                vm.openIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://redzikowski.com")))
            }
            ActionRow(Icons.Rounded.Email, tr("Kontakt"), "kontakt@redzikowski.com") {
                vm.openIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:kontakt@redzikowski.com")))
            }
            ActionRow(Icons.Rounded.LocalCafe, tr("Postaw kawę"), tr("Wesprzyj rozwój na buycoffee.to")) {
                vm.openIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://buycoffee.to/adamredzikowski")))
            }
        }

        Column(Modifier.padding(horizontal = 6.dp)) {
            Text("OpenDial ${BuildConfig.VERSION_NAME}", color = Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(tr("Bez reklam, bez śledzenia, bez kont. Dane zostają w telefonie – aplikacja łączy się z internetem tylko po aktualizacje i z platformą asystenta AI, jeśli go włączysz."), color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (confirmClear) {
        DarkDialog(
            tr("Wyczyścić historię?"),
            onDismiss = { confirmClear = false },
            buttons = {
                PillButton(tr("Anuluj"), primary = false) { confirmClear = false }
                PillButton(tr("Wyczyść"), danger = true) {
                    confirmClear = false
                    vm.clearCallLog()
                }
            },
        ) {
            Text(tr("Usunie wszystkie wpisy rejestru połączeń z telefonu ({0}). Tego nie da się cofnąć.", vm.calls.size), color = Palette.TextDim, fontSize = 13.sp)
        }
    }
    if (simPicker) {
        ChoiceDialog(
            tr("Domyślna karta SIM"),
            listOf(Icons.Rounded.SimCard to tr("Pytaj za każdym razem")) + vm.sims.map { Icons.Rounded.SimCard to (it.label + (it.number?.let { n -> " · $n" } ?: "")) },
            onDismiss = { simPicker = false },
        ) { i ->
            simPicker = false
            set { it.copy(defaultSim = if (i == 0) null else vm.sims[i - 1].id) }
        }
    }
    if (startPicker) {
        val tabs = StartTab.entries.filter { s.showFavoritesTab || it != StartTab.FAVORITES }
        ChoiceDialog(tr("Ekran startowy"), tabs.map { Icons.Rounded.Home to startLabel(it) }, onDismiss = { startPicker = false }) { i ->
            startPicker = false
            set { it.copy(startTab = tabs[i]) }
        }
    }
}

private fun startLabel(t: StartTab) = when (t) {
    StartTab.FAVORITES -> tr("Ulubione")
    StartTab.RECENTS -> tr("Telefon")
    StartTab.CONTACTS -> tr("Kontakty")
    StartTab.KEYPAD -> tr("Telefon z klawiaturą")
    StartTab.LAST -> tr("Ostatnio otwarta zakładka")
}
