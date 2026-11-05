package com.blacksingularities.opencall.ui

import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddIcCall
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.call.CallView
import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.components.ActionRow
import com.blacksingularities.opencall.ui.components.DarkDialog
import com.blacksingularities.opencall.ui.components.DarkField
import com.blacksingularities.opencall.ui.components.PillButton
import com.blacksingularities.opencall.ui.theme.GlassIconButton
import com.blacksingularities.opencall.ui.theme.MonoDigits
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun InCallScreen(
    calls: List<CallView>,
    audio: CallAudioState?,
    ended: CallView?,
    settings: AppSettings,
    dialpadRequested: Boolean,
    onDialpadShown: () -> Unit,
    onAddCall: () -> Unit,
    onMinimize: () -> Unit,
) {
    val primary = CallManager.primary(calls)
    val shown = primary ?: ended
    var dialpad by remember { mutableStateOf(false) }
    var replies by remember { mutableStateOf(false) }
    var routes by remember { mutableStateOf(false) }
    var members by remember { mutableStateOf(false) }
    var rttPanel by remember { mutableStateOf(false) }
    LaunchedEffect(dialpadRequested) {
        if (dialpadRequested) {
            dialpad = true
            onDialpadShown()
        }
    }

    val glow by animateColorAsState(
        when {
            primary == null -> Palette.Record
            primary.ringing -> Palette.Answer
            primary.held -> Palette.Hold
            else -> Palette.Talk
        },
        tween(600),
        label = "glow",
    )
    val breathe = rememberInfiniteTransition(label = "bg")
    val glowAlpha by breathe.animateFloat(0.22f, 0.36f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "glowAlpha")
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Call.Ink)
            .drawBehind {
                // Miękka poświata za awatarem: zielona – dzwoni, niebieska – rozmowa, czerwona – koniec.
                drawRect(
                    Brush.radialGradient(
                        listOf(glow.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width / 2, size.height * 0.32f),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = size.height * 0.55f))
            }
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (shown == null) return@Box
        val ringing = primary?.ringing == true
        val others = calls.filter { it.call != primary?.call }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!ringing && primary != null) {
                    GlassIconButton(Icons.Rounded.ExpandMore, tr("Zwiń do aplikacji"), size = 40.dp, onClick = onMinimize)
                } else {
                    Spacer(Modifier.size(40.dp))
                }
                Spacer(Modifier.weight(1f))
                if (shown.emergency) {
                    Text(tr("Połączenie alarmowe"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Record).padding(horizontal = 10.dp, vertical = 4.dp))
                    Spacer(Modifier.width(6.dp))
                }
                // Jakość i droga połączenia: Wi-Fi Calling, dźwięk HD (VoLTE / VoWiFi).
                listOfNotNull(
                    tr("Wi-Fi").takeIf { shown.wifi },
                    tr("HD").takeIf { shown.hd },
                ).forEach { tag ->
                    Text(
                        tag,
                        color = Palette.Call.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .semantics { contentDescription = if (tag == tr("HD")) tr("Dźwięk HD") else tr("Połączenie przez Wi-Fi") }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                shown.accountLabel?.let {
                    Text(it, color = Palette.Call.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(40.dp))
            }
            // Pasek drugiego połączenia: zawieszone / oczekujące – dotknięcie zamienia.
            others.forEach { o ->
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .reactive(tr("Przełącz na to połączenie"), enabled = !ringing && o.held) { CallManager.swap() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(o.title, o.photoUri, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.title.ifBlank { tr("Numer prywatny") }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (o.held) tr("Zawieszone · dotknij, aby wrócić") else stateText(o), fontSize = 11.sp, color = Palette.Call.TextDim)
                    }
                    Icon(if (o.held) Icons.Rounded.PhonePaused else Icons.Rounded.PhoneInTalk, null, tint = Palette.Call.TextDim)
                }
            }

            Spacer(Modifier.weight(0.6f))
            CallerHeader(shown, primary == null, settings)
            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = when {
                    primary == null -> 0
                    ringing -> 1
                    else -> 2
                },
                transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 4 }) togetherWith fadeOut(tween(150)) },
                label = "callMode",
            ) { mode ->
                when (mode) {
                    1 -> if (primary != null) IncomingControls(primary, others, settings, onReplies = { replies = true })
                    2 -> if (primary != null) OngoingControls(
                        primary, calls, audio,
                        onDialpad = { dialpad = true },
                        onRoutes = { routes = true },
                        onAddCall = onAddCall,
                        onMembers = { members = true },
                        onRtt = {
                            if (!primary.rtt) CallManager.requestRtt(primary.call)
                            rttPanel = true
                        },
                    )
                    else -> Spacer(Modifier.height(140.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        AnimatedVisibility(
            visible = dialpad && primary != null && !ringing,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (primary != null) InCallDialpad(primary, settings) { dialpad = false }
        }
    }

    if (replies && primary != null) {
        DarkDialog(tr("Odrzuć z wiadomością"), onDismiss = { replies = false }, buttons = { PillButton(tr("Anuluj"), primary = false) { replies = false } }) {
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
                settings.quickReplies.forEach { r ->
                    ActionRow(Icons.Rounded.Sms, tr(r)) {
                        replies = false
                        CallManager.reject(primary.call, tr(r))
                    }
                }
            }
            Text(tr("Wiadomość wyśle domyślna aplikacja SMS. Treści zmienisz w ustawieniach."), color = Palette.Call.TextDim, fontSize = 12.sp)
        }
    }
    if (rttPanel && primary != null && !primary.ended) {
        RttPanel(primary) { rttPanel = false }
    }
    RttRequestDialog(onAccepted = { rttPanel = true })
    if (routes && audio != null) {
        AudioRouteDialog(audio) { routes = false }
    }
    if (members && primary != null && primary.conference) {
        val list = remember(primary, calls) { CallManager.conferenceMembers(primary.call) }
        DarkDialog(tr("Uczestnicy konferencji"), onDismiss = { members = false }, buttons = { PillButton(tr("Zamknij"), primary = false) { members = false } }) {
            list.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.title, m.photoUri, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(m.title.ifBlank { tr("Numer prywatny") }, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    GlassIconButton(Icons.AutoMirrored.Rounded.CallSplit, tr("Rozmawiaj osobno"), size = 36.dp) { m.call.splitFromConference() }
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(Icons.Rounded.CallEnd, tr("Rozłącz uczestnika"), size = 36.dp, background = Palette.Record) { m.call.disconnect() }
                }
            }
        }
    }
}

private fun stateText(c: CallView): String = when {
    c.ringing -> if (c.video) tr("Połączenie wideo") else tr("Połączenie przychodzące")
    c.dialing -> tr("Łączenie…")
    c.held -> tr("Zawieszone")
    c.ended -> tr("Połączenie zakończone")
    else -> ""
}

/** Imię, numer, typ numeru, stan i licznik czasu rozmowy. */
@Composable
private fun CallerHeader(c: CallView, ended: Boolean, settings: AppSettings) {
    val pulse = rememberInfiniteTransition(label = "ring")
    val scale by pulse.animateFloat(1f, 1.05f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ringScale")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800)), label = "wave")
    val waves = (c.ringing || c.dialing) && !ended
    val waveColor = if (c.ringing) Palette.Answer else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(200.dp)
                .drawBehind {
                    if (!waves) return@drawBehind
                    // Trzy rozchodzące się kręgi – „dzwoni” widać bez czytania.
                    val base = 66.dp.toPx()
                    for (i in 0 until 3) {
                        val t = (wave + i / 3f) % 1f
                        drawCircle(waveColor.copy(alpha = (1f - t) * 0.35f), radius = base + t * (size.minDimension / 2 - base), style = Stroke(2.dp.toPx()))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Avatar(c.title, c.photoUri, 128.dp, modifier = if (c.ringing) Modifier.scale(scale) else Modifier)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.title.ifBlank { tr("Numer prywatny") },
            fontSize = if (c.title.length > 18) 26.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = buildList {
            if (c.name != null && c.number.isNotBlank()) add(PhoneText.pretty(c.number))
            c.label?.let { add(it) }
            if (c.conference) add(tr("Konferencja · {0}", c.children))
        }.joinToString(" · ")
        if (sub.isNotBlank()) Text(sub, fontSize = 15.sp, color = Palette.Call.TextDim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        if (ended) {
            val dur = if (c.connectTime > 0) PhoneText.duration((System.currentTimeMillis() - c.connectTime) / 1000) else null
            Text(
                listOfNotNull(endReason(c.disconnect), dur).joinToString(" · "),
                color = Palette.Record,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else if (c.active && c.connectTime > 0) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(c.connectTime) {
                while (true) {
                    now = System.currentTimeMillis()
                    delay(500)
                }
            }
            val blink by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "live")
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Answer.copy(alpha = blink)))
                Spacer(Modifier.width(8.dp))
                Text(PhoneText.duration(((now - c.connectTime) / 1000).coerceAtLeast(0)), style = MonoDigits.copy(fontSize = 18.sp), color = Color.White)
            }
        } else if (c.held) {
            Text(tr("Zawieszone"), color = Palette.Hold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(stateText(c), color = Palette.Call.TextDim, fontSize = 15.sp)
        }
    }
}

private fun endReason(d: DisconnectCause?): String = when (d?.code) {
    DisconnectCause.BUSY -> tr("Zajęte")
    DisconnectCause.REJECTED -> tr("Odrzucone")
    DisconnectCause.MISSED -> tr("Nieodebrane")
    DisconnectCause.ERROR -> d.label?.toString()?.takeIf { it.isNotBlank() } ?: tr("Błąd połączenia")
    DisconnectCause.RESTRICTED -> tr("Połączenie zablokowane")
    else -> tr("Połączenie zakończone")
}

/** Dzwoni: suwak (w prawo – odbierz, w lewo – odrzuć) albo dwa przyciski; wiadomość i opcje drugiego połączenia. */
@Composable
private fun IncomingControls(c: CallView, others: List<CallView>, settings: AppSettings, onReplies: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (others.isNotEmpty()) {
            // Połączenie oczekujące podczas rozmowy.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(tr("Zawieś i odbierz"), icon = Icons.Rounded.PhonePaused) { CallManager.holdAndAnswer(c.call) }
                PillButton(tr("Zakończ i odbierz"), primary = false, icon = Icons.Rounded.CallEnd) { CallManager.endAndAnswer(c.call) }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                BigButton(Icons.Rounded.CallEnd, tr("Odrzuć"), Palette.Record) { CallManager.reject(c.call) }
                BigButton(Icons.Rounded.Sms, tr("Wiadomość"), Color.White.copy(alpha = 0.12f), onClick = onReplies)
            }
            return@Column
        }
        var silenced by remember(c.call) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 22.dp)) {
            PillButton(
                if (silenced) tr("Wyciszono") else tr("Wycisz dzwonek"),
                primary = false,
                icon = Icons.AutoMirrored.Rounded.VolumeOff,
            ) {
                silenced = true
                CallManager.silenceRinger()
            }
            PillButton(tr("Wiadomość"), primary = false, icon = Icons.Rounded.Sms, onClick = onReplies)
        }
        // Z czytnikiem ekranu przesuwanie gałki jest niewygodne – zawsze dwa przyciski.
        if (settings.swipeToAnswer && !rememberTouchExploration()) {
            AnswerSlider(onAnswer = { CallManager.answer(c.call) }, onDecline = { CallManager.reject(c.call) })
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                BigButton(Icons.Rounded.CallEnd, tr("Odrzuć"), Palette.Record) { CallManager.reject(c.call) }
                BigButton(Icons.Rounded.Call, tr("Odbierz"), Palette.Answer) { CallManager.answer(c.call) }
            }
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, color: Color, small: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .reactive(label, onClick = onClick)
                .size(if (small) 56.dp else 72.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(if (small) 26.dp else 32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = Palette.Call.TextDim, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Czy działa TalkBack (eksploracja dotykiem); śledzi włączanie i wyłączanie w trakcie. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val am = remember { context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    var on by remember { mutableStateOf(am?.isTouchExplorationEnabled == true) }
    androidx.compose.runtime.DisposableEffect(am) {
        val l = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        am?.addTouchExplorationStateChangeListener(l)
        onDispose { am?.removeTouchExplorationStateChangeListener(l) }
    }
    return on
}

/** Suwak odbierania: gałka na środku; przeciągnięcie do prawej krawędzi odbiera, do lewej – odrzuca. */
@Composable
private fun AnswerSlider(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clearAndSetSemantics {
                contentDescription = tr("Połączenie przychodzące. Przesuń w prawo, aby odebrać, w lewo, aby odrzucić")
                onClick(label = tr("Odbierz")) { onAnswer(); true }
                customActions = listOf(
                    CustomAccessibilityAction(tr("Odbierz")) { onAnswer(); true },
                    CustomAccessibilityAction(tr("Odrzuć")) { onDecline(); true },
                )
            }
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        val knob = 60.dp
        val maxPx = with(density) { ((maxWidth - knob) / 2 - 6.dp).toPx() }
        val progress = (offset.value / maxPx).coerceIn(-1f, 1f)
        val hint = rememberInfiniteTransition(label = "hint")
        val shimmer by hint.animateFloat(0f, 1f, infiniteRepeatable(tween(1400)), label = "shimmer")
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Palette.Record.copy(alpha = 0.2f + 0.6f * (-progress).coerceAtLeast(0f))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CallEnd, tr("Odrzuć"), tint = Color.White)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                // Strzałki „płyną” od gałki ku obu krawędziom.
                for (i in 2 downTo 0) Chevron("‹", shimmer, i)
                Spacer(Modifier.width(knob + 4.dp))
                for (i in 0..2) Chevron("›", shimmer, i)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(Palette.Answer.copy(alpha = 0.2f + 0.6f * progress.coerceAtLeast(0f))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Call, tr("Odbierz"), tint = Color.White)
            }
        }
        val knobColor = when {
            progress > 0.05f -> Palette.Answer
            progress < -0.05f -> Palette.Record
            else -> Color.White
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knob)
                .clip(CircleShape)
                .background(knobColor)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val p = offset.value / maxPx
                            when {
                                p > 0.8f -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAnswer()
                                }
                                p < -0.8f -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDecline()
                                }
                            }
                            scope.launch { offset.animateTo(0f, tween(220)) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(-maxPx, maxPx)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (progress < -0.05f) Icons.Rounded.CallEnd else Icons.Rounded.Call,
                tr("Odbierz lub odrzuć"),
                tint = if (knobColor == Color.White) Palette.Call.Ink else Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Jedna strzałka podpowiedzi; [index] przesuwa fazę, więc trzy razem dają efekt „płynięcia”. */
@Composable
private fun Chevron(glyph: String, phase: Float, index: Int) {
    val a = 1f - (((phase * 3f) - index + 3f) % 3f) / 3f
    Text(glyph, color = Color.White.copy(alpha = 0.15f + 0.55f * a), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 1.dp))
}

/** Trwa rozmowa: siatka przełączników i duży czerwony przycisk rozłączenia. */
@Composable
private fun OngoingControls(
    c: CallView,
    calls: List<CallView>,
    audio: CallAudioState?,
    onDialpad: () -> Unit,
    onRoutes: () -> Unit,
    onAddCall: () -> Unit,
    onMembers: () -> Unit,
    onRtt: () -> Unit,
) {
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val hasBt = audio != null && (audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
    val two = calls.size > 1
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val buttons = listOf(
            Toggle(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, tr("Wycisz"), muted, enabled = true) { CallManager.mute(!muted) },
            Toggle(Icons.Rounded.Dialpad, tr("Klawiatura"), false, onClick = onDialpad),
            Toggle(
                when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headset
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> audioDeviceName(audio) ?: tr("Bluetooth")
                    CallAudioState.ROUTE_WIRED_HEADSET -> tr("Słuchawki")
                    else -> tr("Głośnik")
                },
                route == CallAudioState.ROUTE_SPEAKER || route == CallAudioState.ROUTE_BLUETOOTH,
            ) { if (hasBt) onRoutes() else CallManager.toggleSpeaker() },
            if (two && !c.conference) {
                Toggle(Icons.AutoMirrored.Rounded.CallMerge, tr("Scal"), false, enabled = calls.any { it.canMerge }) { CallManager.merge() }
            } else {
                Toggle(if (c.held) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (c.held) tr("Wznów") else tr("Zawieś"), c.held, enabled = c.canHold || c.held) { CallManager.toggleHold(c.call) }
            },
            if (two) {
                Toggle(Icons.Rounded.SwapCalls, tr("Zamień"), false) { CallManager.swap() }
            } else {
                Toggle(Icons.Rounded.AddIcCall, tr("Dodaj"), false, enabled = c.active, onClick = onAddCall)
            },
            if (c.conference) {
                Toggle(Icons.Rounded.Groups, tr("Uczestnicy"), false, onClick = onMembers)
            } else {
                // SMS do rozmówcy w trakcie rozmowy (np. adres albo numer konta).
                Toggle(Icons.Rounded.Sms, tr("Wiadomość"), false, enabled = c.number.isNotBlank()) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", c.number, null))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            },
        )
        val all = if (c.rttCapable || c.rtt) {
            buttons + Toggle(Icons.Rounded.Keyboard, tr("Tekst RTT"), c.rtt, onClick = onRtt)
        } else {
            buttons
        }
        all.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { t -> ToggleButton(t) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .reactive(tr("Rozłącz")) { CallManager.hangup(c.call) }
                .size(width = 150.dp, height = 72.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(listOf(Color(0xFFFF5A64), Palette.Record))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

/** Rozmowa tekstowa w czasie rzeczywistym: litery idą do rozmówcy już w trakcie pisania. */
@Composable
private fun RttPanel(c: CallView, onClose: () -> Unit) {
    val tick by CallManager.tick.collectAsState()
    val lines = remember(tick, c.call) { CallManager.rttTranscript(c.call) }
    var draft by remember(c.call) { mutableStateOf("") }
    fun commit() {
        if (draft.isBlank()) return
        CallManager.sendRtt(c.call, "
")
        CallManager.rttCommitMine(c.call, draft)
        draft = ""
    }
    DarkDialog(
        tr("Rozmowa tekstowa (RTT)"),
        onDismiss = onClose,
        buttons = {
            if (c.rtt) PillButton(tr("Wyłącz RTT"), primary = false) {
                CallManager.stopRtt(c.call)
                onClose()
            }
            PillButton(tr("Zamknij"), primary = false, onClick = onClose)
        },
    ) {
        if (!c.rtt) {
            Text(tr("Czekam, aż sieć i rozmówca włączą RTT…"), color = Palette.Call.TextDim, fontSize = 13.sp)
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxWidth().height(260.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(lines.size) { i ->
                val l = lines[i]
                Box(Modifier.fillMaxWidth(), contentAlignment = if (l.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        l.text.trimEnd('
'),
                        fontSize = 15.sp,
                        color = if (l.mine) Palette.Call.Ink else Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (l.mine) Color.White else Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        DarkField(
            value = draft,
            onChange = { v ->
                if (!c.rtt) return@DarkField
                // Różnica względem poprzedniego tekstu: dopisane znaki albo backspace.
                val common = draft.commonPrefixWith(v).length
                val out = "".repeat(draft.length - common) + v.substring(common)
                if (v.endsWith("
")) {
                    CallManager.sendRtt(c.call, out.dropLast(1))
                    draft = v.dropLast(1)
                    commit()
                } else {
                    CallManager.sendRtt(c.call, out)
                    draft = v
                }
            },
            label = tr("Pisz – rozmówca widzi każdą literę"),
            singleLine = false,
            trailing = {
                GlassIconButton(Icons.AutoMirrored.Rounded.Send, tr("Wyślij"), size = 36.dp, enabled = c.rtt && draft.isNotBlank()) { commit() }
            },
        )
    }
}

/** Rozmówca prosi o włączenie RTT. */
@Composable
private fun RttRequestDialog(onAccepted: () -> Unit) {
    val tick by CallManager.tick.collectAsState()
    remember(tick) { CallManager.rttRequest } ?: return
    DarkDialog(
        tr("Rozmowa tekstowa"),
        onDismiss = { CallManager.answerRttRequest(false) },
        buttons = {
            PillButton(tr("Odrzuć"), primary = false) { CallManager.answerRttRequest(false) }
            PillButton(tr("Włącz RTT")) {
                CallManager.answerRttRequest(true)
                onAccepted()
            }
        },
    ) {
        Text(tr("Rozmówca chce pisać w trakcie rozmowy (RTT). Głos nadal działa."), fontSize = 14.sp)
    }
}

private class Toggle(val icon: ImageVector, val label: String, val on: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
private fun ToggleButton(t: Toggle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        GlassIconButton(t.icon, t.label, size = 64.dp, active = t.on, enabled = t.enabled, state = if (t.on) tr("włączone") else null, onClick = t.onClick)
        Spacer(Modifier.height(6.dp))
        Text(t.label, fontSize = 12.sp, color = if (t.enabled) Palette.Call.TextDim else Palette.Call.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Suppress("DEPRECATION")
private fun audioDeviceName(a: CallAudioState?): String? =
    runCatching { a?.activeBluetoothDevice?.name }.getOrNull()

/** Wybór wyjścia dźwięku: słuchawka, głośnik, przewodowe, urządzenia Bluetooth. */
@Composable
private fun AudioRouteDialog(a: CallAudioState, onDismiss: () -> Unit) {
    DarkDialog(tr("Dźwięk rozmowy"), onDismiss = onDismiss, buttons = { PillButton(tr("Zamknij"), primary = false, onClick = onDismiss) }) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))) {
            val mask = a.supportedRouteMask
            if (mask and CallAudioState.ROUTE_EARPIECE != 0) {
                RouteRow(Icons.Rounded.PhoneAndroid, tr("Słuchawka telefonu"), a.route == CallAudioState.ROUTE_EARPIECE) {
                    CallManager.route(CallAudioState.ROUTE_EARPIECE)
                    onDismiss()
                }
            }
            if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) {
                RouteRow(Icons.Rounded.Headset, tr("Słuchawki przewodowe"), a.route == CallAudioState.ROUTE_WIRED_HEADSET) {
                    CallManager.route(CallAudioState.ROUTE_WIRED_HEADSET)
                    onDismiss()
                }
            }
            RouteRow(Icons.AutoMirrored.Rounded.VolumeUp, tr("Głośnik"), a.route == CallAudioState.ROUTE_SPEAKER) {
                CallManager.route(CallAudioState.ROUTE_SPEAKER)
                onDismiss()
            }
            @Suppress("DEPRECATION")
            val devices = runCatching { a.supportedBluetoothDevices.toList() }.getOrDefault(emptyList())
            if (devices.isEmpty() && mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
                RouteRow(Icons.Rounded.Bluetooth, tr("Bluetooth"), a.route == CallAudioState.ROUTE_BLUETOOTH) {
                    CallManager.route(CallAudioState.ROUTE_BLUETOOTH)
                    onDismiss()
                }
            }
            devices.forEach { dev ->
                @Suppress("DEPRECATION", "MissingPermission")
                val active = a.route == CallAudioState.ROUTE_BLUETOOTH && runCatching { a.activeBluetoothDevice?.address == dev.address }.getOrDefault(false)
                @Suppress("MissingPermission")
                val name = runCatching { dev.name }.getOrNull() ?: tr("Urządzenie Bluetooth")
                RouteRow(Icons.Rounded.Bluetooth, name, active) {
                    CallManager.bluetooth(dev)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun RouteRow(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    ActionRow(icon, label, if (active) tr("Używane teraz") else null, onClick = onClick)
}

/** Klawiatura w trakcie rozmowy: wysyła tony DTMF (infolinie, poczta głosowa). */
@Composable
private fun InCallDialpad(c: CallView, settings: AppSettings, onClose: () -> Unit) {
    var typed by remember { mutableStateOf(CallManager.dtmfHistory) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Palette.Call.Panel)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            typed.ifEmpty { " " },
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            textAlign = TextAlign.Center,
        )
        Keypad(
            haptics = settings.keypadVibrate,
            onKey = { k ->
                CallManager.dtmf(c.call, k)
                typed += k
            },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(tr("Ukryj klawiaturę"), primary = false, onClick = onClose)
            Box(
                Modifier
                    .reactive(tr("Rozłącz")) { CallManager.hangup(c.call) }
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Palette.Record),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
