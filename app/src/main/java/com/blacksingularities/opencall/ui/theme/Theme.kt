package com.blacksingularities.opencall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blacksingularities.opencall.data.ThemeMode

/**
 * Bieżący tryb jasny/ciemny. Paleta czyta go w getterach, więc zmiana przerysowuje każdy
 * ekran, który użył koloru – bez przekazywania motywu przez wszystkie funkcje.
 */
object ThemeState {
    var dark by mutableStateOf(true)

    /** Tryb głównego okna – przywracany, gdy wraca ono na pierwszy plan po ekranie rozmowy. */
    var appDark = true
}

object Palette {
    private val dark: Boolean get() = ThemeState.dark

    // Czarno-biały interfejs: akcent = kolor tekstu (biel w ciemnym, czerń w jasnym), czerwień dla
    // rozłączania i usuwania, zieleń tylko dla odbierania połączeń.
    /** Kolor treści: tekst i ikony na tle aplikacji. */
    val Fg: Color get() = if (dark) Color.White else Color(0xFF111111)
    val Amber: Color get() = Fg
    val Coral: Color get() = if (dark) Color(0xFFE0E0E0) else Color(0xFF333333)
    val Moon: Color get() = if (dark) Color(0xFFBDBDBD) else Color(0xFF616161)
    /** Tło aplikacji, a zarazem kolor treści na akcencie (np. tekst na białym przycisku). */
    val Ink: Color get() = if (dark) Color.Black else Color.White
    val Panel: Color get() = Ink
    val PanelHigh: Color get() = if (dark) Color(0xFF1C1C1C) else Color(0xFFEFEFEF)
    val Glass: Color get() = Fg.copy(alpha = 0.16f)
    val TextDim: Color get() = Fg.copy(alpha = if (dark) 0.70f else 0.66f)
    val TextFaint: Color get() = Fg.copy(alpha = 0.40f)

    val Record = Color(0xFFFF3B47)
    val Answer = Color(0xFF2FD073)
    /** Poświata ekranu rozmowy: trwająca rozmowa i zawieszone połączenie. */
    val Talk = Color(0xFF4F7CFF)
    val Hold = Color(0xFFFFB020)

    /** Tła awatarów bez zdjęcia – stonowane, żeby nie łamać czarno-białego interfejsu. */
    val AvatarTints: List<Color> get() = if (dark) darkTints else lightTints
    private val darkTints = listOf(
        Color(0xFF2A2A2A), Color(0xFF33302A), Color(0xFF2A3033), Color(0xFF302A33),
        Color(0xFF2A332D), Color(0xFF33292B), Color(0xFF2D2D36), Color(0xFF36332A),
    )
    private val lightTints = listOf(
        Color(0xFFE6E6E6), Color(0xFFEDE8DE), Color(0xFFDEE8ED), Color(0xFFE8DEED),
        Color(0xFFDEEDE2), Color(0xFFEDDEE0), Color(0xFFE2E2EE), Color(0xFFEEEADE),
    )

    /** Stałe ciemne kolory ekranu rozmowy – on zawsze jest ciemny, jak w każdym telefonie. */
    object Call {
        val Fg = Color.White
        val Ink = Color.Black
        val TextDim = Color(0xB3FFFFFF)
        val TextFaint = Color(0x66FFFFFF)
        val Panel = Color(0xFF1C1C1C)
    }
}

private fun scheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = Color.White, onPrimary = Color.Black,
        secondary = Color(0xFFE0E0E0), onSecondary = Color.Black,
        background = Color.Black, onBackground = Color.White,
        surface = Color.Black, onSurface = Color.White,
        surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xB3FFFFFF),
        surfaceContainerLow = Color.Black, surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF1C1C1C), surfaceContainerHighest = Color(0xFF1C1C1C),
        outline = Color(0x33FFFFFF), error = Palette.Record,
    )
} else {
    lightColorScheme(
        primary = Color(0xFF111111), onPrimary = Color.White,
        secondary = Color(0xFF333333), onSecondary = Color.White,
        background = Color.White, onBackground = Color(0xFF111111),
        surface = Color.White, onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFEFEFEF), onSurfaceVariant = Color(0xA8111111),
        surfaceContainerLow = Color.White, surfaceContainer = Color.White,
        surfaceContainerHigh = Color(0xFFEFEFEF), surfaceContainerHighest = Color(0xFFEFEFEF),
        outline = Color(0x33111111), error = Palette.Record,
    )
}

val MonoDigits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 14.sp)

/** Czy dany tryb daje ciemny wygląd (SYSTEM – według ustawień telefonu). */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/**
 * Motyw aplikacji. [dark] ustawia też [ThemeState] dla [Palette]. Ekran rozmowy woła wersję
 * bez argumentów: zostaje ciemny i nie przestawia trybu głównego okna.
 */
@Composable
fun OpenDialTheme(dark: Boolean? = null, onDarkChanged: (Boolean) -> Unit = {}, content: @Composable () -> Unit) {
    if (dark != null) {
        ThemeState.appDark = dark
        if (ThemeState.dark != dark) ThemeState.dark = dark
    }
    val d = dark ?: true
    SideEffect { onDarkChanged(d) }
    MaterialTheme(colorScheme = scheme(d), typography = Typography(), content = content)
}
