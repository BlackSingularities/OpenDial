package com.blacksingularities.opencall.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.CONTACT_SCALE_MAX
import com.blacksingularities.opencall.data.CONTACT_SCALE_MIN
import com.blacksingularities.opencall.data.contactColumns
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Powiększenie listy gestem: płynna skala wierszy, a od pewnego progu – kafelki. */
@Stable
class ListZoom(initial: Float) {
    var scale by mutableFloatStateOf(initial.coerceIn(CONTACT_SCALE_MIN, CONTACT_SCALE_MAX))
    var zooming by mutableStateOf(false)

    /** 1 = lista, 2–4 = kafelki w rzędzie. */
    val columns: Int get() = contactColumns(scale)
}

@Composable
fun rememberListZoom(initial: Float): ListZoom = remember { ListZoom(initial) }

/**
 * Szczypanie dwoma palcami jak w OpenSMS: zdarzenia czytane przed listą (faza Initial) i zjadane
 * tylko przy co najmniej dwóch palcach – przewijanie, dotknięcia i przytrzymania działają bez zmian.
 * Skala zmienia się na bieżąco; zmiana liczby kolumn daje lekkie stuknięcie. [onSave] – po geście.
 */
@Composable
fun Modifier.pinchZoom(zoom: ListZoom, haptics: Boolean, onSave: (Float) -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    val save by rememberUpdatedState(onSave)
    val tick by rememberUpdatedState(haptics)
    return this.pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    zoom.zooming = true
                    val z = event.calculateZoom()
                    if (z != 1f) {
                        val before = zoom.columns
                        zoom.scale = (zoom.scale * z).coerceIn(CONTACT_SCALE_MIN, CONTACT_SCALE_MAX)
                        if (zoom.columns != before && tick) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    event.changes.forEach { it.consume() }
                } else if (pinching) {
                    // Po odjęciu jednego palca reszta gestu nie przewija listy.
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            if (pinching) {
                zoom.zooming = false
                // Blisko 100% – dokładnie 100%.
                if (abs(zoom.scale - 1f) < 0.05f) zoom.scale = 1f
                save(zoom.scale)
            }
        }
    }
}

/** Treść listy w skali powiększenia (kafelki mają własny rozmiar zależny od liczby kolumn). */
@Composable
fun ZoomedDensity(zoom: ListZoom, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val s = if (zoom.columns == 1) zoom.scale else 1f
    CompositionLocalProvider(LocalDensity provides Density(base.density * s, base.fontScale), content = content)
}

/** Pigułka z bieżącym powiększeniem; dotknięcie wraca do 100%. Znika chwilę po geście. */
@Composable
fun ZoomPill(zoom: ListZoom, modifier: Modifier = Modifier, onReset: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(zoom.zooming, zoom.scale) {
        if (zoom.zooming) {
            visible = true
        } else {
            delay(1200)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.PanelHigh)
                .reactive(tr("Przywróć 100%"), pressScale = 0.95f, onClick = onReset)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tiles = zoom.columns > 1
            Icon(if (tiles) Icons.Rounded.GridView else Icons.Rounded.ZoomIn, null, tint = Palette.Fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (tiles) tr("Kafelki · {0} w rzędzie", zoom.columns) else "${(zoom.scale * 100).roundToInt()}%",
                color = Palette.Fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            if (zoom.scale != 1f) Text("  ·  " + tr("dotknij: 100%"), color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}
