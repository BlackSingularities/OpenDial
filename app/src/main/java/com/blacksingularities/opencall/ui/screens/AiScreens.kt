package com.blacksingularities.opencall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.ai.AiHandoff
import com.blacksingularities.opencall.ai.AiLanguages
import com.blacksingularities.opencall.ai.AiPrompt
import com.blacksingularities.opencall.ai.AiProvider
import com.blacksingularities.opencall.ai.AiRecord
import com.blacksingularities.opencall.ai.AiRules
import com.blacksingularities.opencall.ai.AiScreening
import com.blacksingularities.opencall.ai.AiStatus
import com.blacksingularities.opencall.ai.AiTone
import com.blacksingularities.opencall.ai.AiVerdict
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Format
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.ChipRow
import com.blacksingularities.opencall.ui.components.ChoiceDialog
import com.blacksingularities.opencall.ui.components.CompactChoice
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.EmptyState
import com.blacksingularities.opencall.ui.components.Group
import com.blacksingularities.opencall.ui.components.Page
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.components.RoundIcon
import com.blacksingularities.opencall.ui.components.RowIcon
import com.blacksingularities.opencall.ui.components.SwitchRow
import com.blacksingularities.opencall.ui.components.TabChip
import com.blacksingularities.opencall.ui.theme.Palette

private fun providerName(p: AiProvider) = when (p) {
    AiProvider.VAPI -> "Vapi"
    AiProvider.RETELL -> "Retell AI"
    AiProvider.ELEVENLABS -> "ElevenLabs Agents"
    AiProvider.CUSTOM -> tr("Własny serwer")
}

private fun providerHint(p: AiProvider) = when (p) {
    AiProvider.VAPI -> tr("Aplikacja sama utworzy agenta i przypisze go do numeru. Numer kupisz lub zaimportujesz w panelu Vapi; streszczenie przy przełączeniu wymaga numeru Twilio.")
    AiProvider.RETELL -> tr("Utwórz agenta w panelu Retell, przypisz go do numeru i opublikuj. Aplikacja ustawi jego rozmowę i przełączanie.")
    AiProvider.ELEVENLABS -> tr("Utwórz agenta w panelu ElevenLabs i przypisz go do numeru Twilio lub SIP. Aplikacja ustawi jego rozmowę i przełączanie.")
    AiProvider.CUSTOM -> tr("Twój serwer przyjmuje konfigurację przez POST i zwraca rozmowy przez GET – format opisuje README.")
}

private fun providerPanel(p: AiProvider) = when (p) {
    AiProvider.VAPI -> "https://dashboard.vapi.ai"
    AiProvider.RETELL -> "https://dashboard.retellai.com"
    AiProvider.ELEVENLABS -> "https://elevenlabs.io/app/agents"
    AiProvider.CUSTOM -> null
}

private fun modelPresets(p: AiProvider): List<String> = when (p) {
    AiProvider.VAPI -> listOf(
        "openai/gpt-5.4-mini",
        "anthropic/claude-haiku-5-5",
        "google/gemini-3.5-flash",
        "groq/llama-3.3-70b-versatile",
    )
    AiProvider.RETELL -> listOf("gpt-5.4-mini", "claude-5.5-haiku", "gemini-3.5-flash")
    AiProvider.ELEVENLABS -> listOf("gpt-5.4-mini", "gemini-3.5-flash")
    AiProvider.CUSTOM -> emptyList()
}

private fun voicePresets(p: AiProvider): List<String> = when (p) {
    AiProvider.VAPI -> listOf("azure/pl-PL-ZofiaNeural", "azure/pl-PL-MarekNeural")
    else -> emptyList()
}

@Composable
private fun Hint(text: String, color: Color = Palette.TextDim) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
private fun Fields(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Step(done: Boolean, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RowIcon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (done) Palette.Answer else Palette.TextDim)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
        }
    }
}

/** Gotowe wartości pod polem tekstowym; dotknięcie wybranej czyści pole. */
@Composable
private fun Presets(values: List<String>, selected: String, onPick: (String) -> Unit) {
    if (values.isEmpty()) return
    ChipRow {
        values.forEach { v ->
            TabChip(null, v.substringAfter('/'), selected == v) { onPick(if (selected == v) "" else v) }
        }
    }
}

/** Ustawienia trybu „Kto dzwoni naprawdę”: od klucza API po sposób rozmowy agenta. */
@Composable
fun AiScreeningScreen(vm: AppViewModel) {
    val a = vm.settings.ai
    fun set(t: (AiScreening) -> AiScreening) = vm.updateAi(t)
    var providerPicker by remember { mutableStateOf(false) }
    var languagePicker by remember { mutableStateOf(false) }
    var hourPicker by remember { mutableStateOf<Int?>(null) }
    var key by remember(vm.aiKey) { mutableStateOf(vm.aiKey) }

    LaunchedEffect(Unit) { vm.loadAi() }
    LaunchedEffect(vm.sims) {
        val own = vm.sims.firstNotNullOfOrNull { it.number }
        if (a.ownNumber.isBlank() && own != null) set { it.copy(ownNumber = own) }
    }

    val connected = vm.aiKey.isNotBlank() && a.agentNumber.isNotBlank()
    val synced = remember(a) { a.syncedHash == AiPrompt.fingerprint(a) }

    Page(tr("Kto dzwoni naprawdę"), onBack = { vm.pop() }, actions = {
        RoundIcon(Icons.Rounded.History, tr("Historia asystenta")) { vm.push(Route.AiHistory) }
    }) {
        Hint(tr("Nieznany numer nie dzwoni. Odbiera AI, pyta o cel i tylko ważne sprawy przełącza do Ciebie, jednym zdaniem streszczając rozmowę. Fotowoltaika i „badania opinii” kończą się na asystencie."))
        Hint(tr("Android nie daje zwykłej aplikacji dostępu do dźwięku rozmowy, więc rozmawia agent głosowy w chmurze, na Twoim koncie i za Twoją opłatą. Telefon odrzuca połączenie, a operator przekierowuje je na numer agenta."))
        if (!vm.isDefaultDialer) {
            Hint(tr("Asystent działa, gdy OpenDial jest domyślną aplikacją telefonu."), Palette.Record)
        }

        Group(null) {
            Step(connected, tr("Połącz platformę"), tr("Klucz API i numer agenta"))
            Step(connected && synced, tr("Wyślij ustawienia do agenta"), if (connected && !synced) tr("Są niewysłane zmiany") else tr("Powitanie, zasady i przełączanie"))
            Step(a.forwardingOn, tr("Włącz przekierowanie"), tr("Kod u operatora, jednorazowo"))
            SwitchRow(
                Icons.Rounded.SmartToy,
                tr("Asystent włączony"),
                tr("Nieznane numery trafiają do AI"),
                a.enabled,
                enabled = a.enabled || (connected && a.forwardingOn),
            ) { v -> set { it.copy(enabled = v) } }
        }

        Group(tr("Platforma")) {
            ActionRow(Icons.Rounded.Hub, tr("Platforma agenta"), providerName(a.provider)) { providerPicker = true }
            Fields {
                Hint(providerHint(a.provider))
                DarkField(key, { key = it }, tr("Klucz API"), password = true)
                if (a.provider == AiProvider.CUSTOM) {
                    DarkField(a.customUrl, { v -> set { it.copy(customUrl = v.trim()) } }, tr("Adres serwera (https://…)"), keyboard = KeyboardType.Uri)
                }
                PillButton(if (vm.aiBusy) tr("Łączenie…") else tr("Połącz"), enabled = !vm.aiBusy && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    vm.saveAiKey(key)
                    vm.aiConnect()
                }
            }
            vm.aiLines.forEach { line ->
                ActionRow(
                    Icons.Rounded.Call,
                    PhoneText.pretty(line.number),
                    if (line.number == a.agentNumber) tr("Wybrany numer") else line.label,
                ) { vm.aiPickLine(line) }
            }
            Fields {
                DarkField(a.agentNumber, { v -> set { it.copy(agentNumber = v.trim()) } }, tr("Numer agenta"), keyboard = KeyboardType.Phone)
                DarkField(a.ownNumber, { v -> set { it.copy(ownNumber = v.trim()) } }, tr("Twój numer"), keyboard = KeyboardType.Phone)
                if (a.provider != AiProvider.CUSTOM) {
                    DarkField(a.agentId, { v -> set { it.copy(agentId = v.trim()) } }, tr("Identyfikator agenta"))
                }
            }
            providerPanel(a.provider)?.let { url ->
                ActionRow(Icons.Rounded.Public, tr("Otwórz panel platformy"), url) { vm.web(url) }
            }
        }

        Group(tr("Przekierowanie u operatora")) {
            if (!a.forwardingOn) {
                CompactChoice(
                    Icons.AutoMirrored.Rounded.PhoneForwarded,
                    tr("Przekazanie"),
                    AiHandoff.entries,
                    a.handoff,
                    { if (it == AiHandoff.BUSY) tr("Od razu: telefon odrzuca, operator przekierowuje") else tr("Telefon dzwoni cicho, możesz odebrać") },
                    short = { if (it == AiHandoff.BUSY) tr("Od razu") else tr("Cicho") },
                ) { v -> set { it.copy(handoff = v) } }
                if (a.handoff == AiHandoff.NO_ANSWER) {
                    CompactChoice(
                        Icons.Rounded.Timer,
                        tr("Czas dzwonienia"),
                        listOf(10, 15, 20, 30),
                        a.noAnswerSeconds,
                        { tr("{0} s", it) },
                    ) { v -> set { it.copy(noAnswerSeconds = v) } }
                }
                ActionRow(
                    Icons.AutoMirrored.Rounded.PhoneForwarded,
                    tr("Włącz przekierowanie"),
                    if (a.agentNumber.isBlank()) tr("Najpierw podaj numer agenta") else AiRules.enableCode(a),
                    enabled = a.agentNumber.isNotBlank(),
                ) { vm.aiForwarding(true) }
            } else {
                ActionRow(Icons.Rounded.PhoneDisabled, tr("Wyłącz przekierowanie"), AiRules.disableCode(a), danger = true) { vm.aiForwarding(false) }
            }
            ActionRow(Icons.Rounded.Call, tr("Zadzwoń do agenta na próbę"), tr("Sprawdzisz powitanie i głos"), enabled = a.agentNumber.isNotBlank()) { vm.call(a.agentNumber) }
            Fields {
                Hint(tr("Przekierowanie działa też, gdy sam odrzucisz połączenie albo masz zajęte – wtedy również odbierze agent. Operator może doliczać opłatę za przekierowane połączenia. Jeśli po odrzuceniu dzwoniący słyszy pocztę lub zajęte, wybierz tryb cichy."))
            }
        }

        Group(tr("Kto trafia do asystenta")) {
            SwitchRow(Icons.Rounded.VisibilityOff, tr("Numery ukryte"), tr("Także połączenia bez numeru"), a.screenHidden) { v -> set { it.copy(screenHidden = v) } }
            CompactChoice(
                Icons.Rounded.Repeat,
                tr("Drugi telefon z rzędu"),
                listOf(0, 3, 10),
                a.repeatMinutes,
                { if (it == 0) tr("Też trafia do asystenta") else tr("W ciągu {0} min dzwoni normalnie", it) },
                short = { if (it == 0) tr("Nie") else tr("{0} min", it) },
            ) { v -> set { it.copy(repeatMinutes = v) } }
            CompactChoice(
                Icons.Rounded.Call,
                tr("Numery, do których dzwoniłeś"),
                listOf(0, 7, 30),
                a.recentOutgoingDays,
                { if (it == 0) tr("Też trafiają do asystenta") else tr("Z ostatnich {0} dni dzwonią normalnie", it) },
                short = { if (it == 0) tr("Nie") else tr("{0} dni", it) },
            ) { v -> set { it.copy(recentOutgoingDays = v) } }
            SwitchRow(Icons.Rounded.FlightTakeoff, tr("Pomijaj w roamingu"), tr("Za granicą telefon dzwoni normalnie – bez opłat za przekierowanie"), a.skipRoaming) { v -> set { it.copy(skipRoaming = v) } }
            SwitchRow(Icons.Rounded.Schedule, tr("Tylko w wybranych godzinach"), tr("Poza nimi telefon dzwoni normalnie"), a.scheduleOn) { v -> set { it.copy(scheduleOn = v) } }
            if (a.scheduleOn) {
                ActionRow(Icons.Rounded.Schedule, tr("Od godziny"), "%02d:00".format(a.fromHour)) { hourPicker = 0 }
                ActionRow(Icons.Rounded.Schedule, tr("Do godziny"), "%02d:00".format(a.toHour)) { hourPicker = 1 }
            }
        }

        Group(tr("Sposób rozmowy")) {
            ActionRow(Icons.Rounded.Translate, tr("Język rozmowy"), AiLanguages.firstOrNull { it.code == a.language }?.name ?: a.language) { languagePicker = true }
            CompactChoice(
                Icons.Rounded.RecordVoiceOver,
                tr("Ton"),
                AiTone.entries,
                a.tone,
                {
                    when (it) {
                        AiTone.FORMAL -> tr("Uprzejmy i oficjalny")
                        AiTone.FRIENDLY -> tr("Ciepły i swobodny")
                        AiTone.BRIEF -> tr("Możliwie krótko")
                    }
                },
                short = {
                    when (it) {
                        AiTone.FORMAL -> tr("Oficjalny")
                        AiTone.FRIENDLY -> tr("Ciepły")
                        AiTone.BRIEF -> tr("Krótki")
                    }
                },
            ) { v -> set { it.copy(tone = v) } }
            CompactChoice(
                Icons.Rounded.Timer,
                tr("Limit rozmowy"),
                listOf(60, 90, 120, 180),
                a.maxSeconds,
                { tr("{0} s", it) },
            ) { v -> set { it.copy(maxSeconds = v) } }
            Fields {
                DarkField(a.ownerName, { v -> set { it.copy(ownerName = v) } }, tr("Twoje imię (agent się nim przedstawi)"))
                DarkField(a.assistantName, { v -> set { it.copy(assistantName = v) } }, tr("Imię asystenta (opcjonalnie)"))
                DarkField(a.greeting, { v -> set { it.copy(greeting = v) } }, tr("Własne powitanie (opcjonalnie)"), singleLine = false, minLines = 2)
                Hint(tr("Dzwoniący usłyszy: {0}", AiPrompt.greeting(a)))
                DarkField(a.importantRules, { v -> set { it.copy(importantRules = v) } }, tr("Co jest ważne – przełącz do mnie"), singleLine = false, minLines = 3)
                DarkField(a.spamRules, { v -> set { it.copy(spamRules = v) } }, tr("Czego nie chcę – zakończ rozmowę"), singleLine = false, minLines = 3)
                DarkField(a.extraRules, { v -> set { it.copy(extraRules = v) } }, tr("Dodatkowe wskazówki dla agenta"), singleLine = false, minLines = 2)
                DarkField(a.model, { v -> set { it.copy(model = v.trim()) } }, tr("Model AI (puste = z panelu platformy)"))
                Presets(modelPresets(a.provider), a.model) { v -> set { it.copy(model = v) } }
                DarkField(a.voice, { v -> set { it.copy(voice = v.trim()) } }, tr("Głos (puste = z panelu platformy)"))
                Presets(voicePresets(a.provider), a.voice) { v -> set { it.copy(voice = v) } }
            }
            if (a.provider == AiProvider.VAPI) {
                SwitchRow(Icons.Rounded.Mic, tr("Nagrywaj rozmowy"), tr("Agent uprzedzi o tym dzwoniącego"), a.record) { v -> set { it.copy(record = v) } }
            }
        }

        Group(tr("Po rozmowie")) {
            SwitchRow(Icons.Rounded.NotificationsActive, tr("Powiadamiaj o spamie"), tr("Domyślnie spam znika bez śladu, zostaje w historii asystenta"), a.notifySpam) { v -> set { it.copy(notifySpam = v) } }
            SwitchRow(Icons.Rounded.Block, tr("Blokuj numery ze spamem"), tr("Dopisuje numer do listy zablokowanych"), a.autoBlockSpam) { v -> set { it.copy(autoBlockSpam = v) } }
            SwitchRow(Icons.Rounded.Description, tr("Zachowuj transkrypcje"), tr("Tylko w telefonie, bez kopii zapasowej"), a.keepTranscripts) { v -> set { it.copy(keepTranscripts = v) } }
        }

        PillButton(
            if (vm.aiBusy) tr("Łączenie…") else tr("Wyślij ustawienia do agenta"),
            enabled = !vm.aiBusy && connected,
            modifier = Modifier.fillMaxWidth(),
        ) { vm.aiSendConfig() }
        Hint(tr("Rozmowy, transkrypcje i klucz API idą wyłącznie do wybranej platformy i podlegają jej regulaminowi. Klucz jest zaszyfrowany w telefonie. Agent zawsze mówi dzwoniącemu, że jest AI."))
        Spacer(Modifier.height(16.dp))
    }

    if (providerPicker) {
        ChoiceDialog(
            tr("Platforma agenta"),
            AiProvider.entries.map { Icons.Rounded.Hub to providerName(it) },
            onDismiss = { providerPicker = false },
        ) { i ->
            providerPicker = false
            vm.setAiProvider(AiProvider.entries[i])
        }
    }
    if (languagePicker) {
        ChoiceDialog(
            tr("Język rozmowy"),
            AiLanguages.map { Icons.Rounded.Translate to it.name },
            onDismiss = { languagePicker = false },
        ) { i ->
            languagePicker = false
            set { it.copy(language = AiLanguages[i].code) }
        }
    }
    hourPicker?.let { which ->
        ChoiceDialog(
            if (which == 0) tr("Od godziny") else tr("Do godziny"),
            (0..23).map { Icons.Rounded.Schedule to "%02d:00".format(it) },
            onDismiss = { hourPicker = null },
        ) { hour ->
            hourPicker = null
            set { if (which == 0) it.copy(fromHour = hour) else it.copy(toHour = hour) }
        }
    }
}

private fun verdictColor(r: AiRecord): Color = when {
    r.status == AiStatus.MISSED -> Palette.Record
    r.open -> Palette.Talk
    r.verdict == AiVerdict.IMPORTANT -> Palette.Answer
    r.verdict == AiVerdict.SPAM -> Palette.Record
    r.verdict == AiVerdict.MESSAGE -> Palette.Hold
    else -> Palette.TextDim
}

private fun verdictLabel(r: AiRecord): String = when {
    r.status == AiStatus.MISSED -> tr("Nie dotarło do agenta")
    r.status == AiStatus.PENDING -> tr("Przekazywanie…")
    r.status == AiStatus.LIVE -> tr("Trwa rozmowa z agentem")
    r.transferred -> tr("Przełączone do Ciebie")
    r.verdict == AiVerdict.IMPORTANT -> tr("Ważne")
    r.verdict == AiVerdict.SPAM -> tr("Spam")
    r.verdict == AiVerdict.MESSAGE -> tr("Wiadomość")
    else -> tr("Rozmowa zakończona")
}

/** Co asystent zrobił z połączeniami: streszczenia, transkrypcje, oddzwanianie. */
@Composable
fun AiHistoryScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) {
        vm.loadAi()
        vm.aiRefresh()
    }
    Page(tr("Historia asystenta"), onBack = { vm.pop() }, actions = {
        RoundIcon(Icons.Rounded.Refresh, tr("Odśwież"), enabled = !vm.aiBusy) { vm.aiRefresh() }
    }) {
        if (vm.aiRecords.isEmpty()) {
            EmptyState(Icons.Rounded.SmartToy, tr("Jeszcze pusto"), tr("Tu pojawią się połączenia odebrane przez asystenta, ze streszczeniem rozmowy."))
        }
        vm.aiRecords.forEach { r -> RecordCard(vm, r) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RecordCard(vm: AppViewModel, r: AiRecord) {
    var open by remember(r.id) { mutableStateOf(false) }
    val who = r.caller ?: r.number.takeIf { it.isNotBlank() }?.let { PhoneText.pretty(it) } ?: tr("Numer prywatny")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { open = !open }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(verdictColor(r)))
            Spacer(Modifier.width(8.dp))
            Text(who, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Text(Format.relative(r.at), fontSize = 11.sp, color = Palette.TextDim)
        }
        Text(verdictLabel(r), fontSize = 11.sp, color = verdictColor(r), fontWeight = FontWeight.SemiBold)
        r.summary?.let { Text(it, fontSize = 13.sp, color = Color.White, lineHeight = 18.sp) }
        if (open) {
            if (r.caller != null && r.number.isNotBlank()) Text(PhoneText.pretty(r.number), fontSize = 12.sp, color = Palette.TextDim)
            r.transcript?.let { Text(it, fontSize = 12.sp, color = Palette.TextDim, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (r.number.isNotBlank()) {
                    PillButton(tr("Oddzwoń"), icon = Icons.Rounded.Call) { vm.call(r.number, r.caller) }
                    PillButton(tr("Zablokuj"), primary = false, icon = Icons.Rounded.Block) { vm.block(r.number) }
                }
                RoundIcon(Icons.Rounded.Delete, tr("Usuń")) { vm.aiDelete(r.id) }
            }
        }
    }
}
