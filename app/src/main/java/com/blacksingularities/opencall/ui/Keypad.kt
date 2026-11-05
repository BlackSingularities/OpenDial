package com.blacksingularities.opencall.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import com.blacksingularities.opencall.ui.theme.LocalHaptics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.reactive

private val keys = listOf(
    listOf('1' to "∞", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/**
 * Klawiatura numeryczna: cyfra z literami T9. [onLongKey] – przytrzymanie (0 → „+”, 1 → poczta
 * głosowa, 2–9 → szybkie wybieranie); gdy zwróci false, przytrzymanie działa jak dotknięcie.
 */
@Composable
fun Keypad(
    haptics: Boolean,
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 72.dp,
    speedDial: Set<Int> = emptySet(),
    onLongKey: ((Char) -> Boolean)? = null,
) {
    // Wibracja klawiszy ma własne ustawienie, niezależne od reszty przycisków.
    CompositionLocalProvider(LocalHaptics provides haptics) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (digit, letters) ->
                    val hasSpeed = digit.digitToIntOrNull()?.let { it in speedDial } == true
                    Box(
                        Modifier
                            .reactive(
                                label = when (digit) {
                                    '1' -> tr("1 · przytrzymaj: poczta głosowa")
                                    '0' -> tr("0 · przytrzymaj: +")
                                    else -> if (hasSpeed) tr("{0} · przytrzymaj: szybkie wybieranie", digit) else digit.toString()
                                },
                                onLongPress = onLongKey?.let { f ->
                                    {
                                        if (!f(digit)) onKey(digit)
                                    }
                                },
                                pressScale = 0.88f,
                            ) { onKey(digit) }
                            .size(keySize)
                            .clip(CircleShape)
                            .background(Palette.Fg.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                digit.toString(),
                                fontSize = (keySize.value * 0.4f).sp,
                                fontWeight = FontWeight.Normal,
                                color = Palette.Fg,
                                lineHeight = (keySize.value * 0.42f).sp,
                            )
                            if (letters.isNotEmpty()) {
                                Text(
                                    letters,
                                    fontSize = (keySize.value * 0.13f).sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    color = if (hasSpeed) Palette.Fg else Palette.TextDim,
                                    lineHeight = (keySize.value * 0.15f).sp,
                                )
                            } else {
                                Box(Modifier.height((keySize.value * 0.15f).dp))
                            }
                        }
                        if (hasSpeed) {
                            Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(CircleShape).background(Palette.Fg))
                        }
                    }
                }
            }
        }
    }
    }
}
