package com.blacksingularities.opencall.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blacksingularities.opencall.i18n.tr
import com.blacksingularities.opencall.ui.theme.BlockShape
import com.blacksingularities.opencall.ui.theme.GlassIconButton
import com.blacksingularities.opencall.ui.theme.Palette
import com.blacksingularities.opencall.ui.theme.glass
import com.blacksingularities.opencall.ui.theme.reactive

/** Okrągły przycisk z ikoną. */
@Composable
fun RoundIcon(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null,
    size: Dp = 44.dp,
    tint: Color? = null,
    onLongPress: (() -> Unit)? = null,
    iconSize: Dp? = null,
    onClick: () -> Unit,
) {
    GlassIconButton(
        icon = icon,
        label = label,
        modifier = modifier,
        size = size,
        active = active,
        enabled = enabled,
        tint = tint,
        badge = badge,
        onLongPress = onLongPress,
        iconSize = iconSize,
        onClick = onClick,
    )
}

/**
 * Ekran podrzędny: nagłówek ze strzałką wstecz, tytułem i akcjami, a pod nim treść
 * (przewijana, gdy [scrollable]).
 */
@Composable
fun Page(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    overlayHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (overlayHeader) {
        // Przyciski unoszą się nad treścią (bez osobnego paska z tłem) – treść przewija się pod nimi.
        Box(
            modifier
                .fillMaxSize()
                .background(Palette.Ink)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 14.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), size = 40.dp, onClick = onBack)
                Spacer(Modifier.width(12.dp))
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            }
        }
        return
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Nagłówek ekranu podrzędnego bez ramki [Page] – dla treści z własnym przewijaniem. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, tr("Wstecz"), size = 40.dp, onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Grupa wierszy na lekko jaśniejszym tle, z opcjonalnym nagłówkiem. */
@Composable
fun Group(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = Palette.Amber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp, top = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Fg.copy(alpha = 0.06f))
                .padding(vertical = 2.dp),
            content = content,
        )
    }
}

/** Nagłówek sekcji: ikona, nazwa i cienka linia do krawędzi. */
@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Palette.Fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Fg.copy(alpha = 0.12f)))
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = Palette.Amber) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!value) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontSize = 14.sp, color = if (enabled) Palette.Fg else Palette.TextFaint)
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp)
        }
        Switch(
            checked = value,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.scale(0.82f),
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Amber,
                checkedThumbColor = Palette.Ink,
                uncheckedTrackColor = Palette.Fg.copy(alpha = 0.08f),
                uncheckedThumbColor = Palette.TextDim,
                uncheckedBorderColor = Palette.Fg.copy(alpha = 0.2f),
            ),
        )
    }
}

/** Wybór w jednej linii z przełącznikiem segmentowym po prawej. */
@Composable
fun <T> CompactChoice(
    rowIcon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    short: (T) -> String = label,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(rowIcon)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label(selected), fontSize = 11.sp, lineHeight = 13.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Palette.Fg.copy(alpha = 0.06f))
                .padding(2.dp),
        ) {
            options.forEach { o ->
                val sel = o == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Palette.Fg else Color.Transparent)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(o) })
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        short(o),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Palette.Ink else Palette.TextDim,
                    )
                }
            }
        }
    }
}

/** Wiersz-akcja: ikona, tytuł, opis; dotknięcie wykonuje akcję. */
@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickableCompat(enabled, onClick, onLongClick)
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon, if (danger) Palette.Record else Palette.Amber)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                color = when {
                    !enabled -> Palette.TextFaint
                    danger -> Palette.Record
                    else -> Palette.Fg
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Palette.TextDim, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

fun Modifier.combinedClickableCompat(enabled: Boolean, onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)

/** Przycisk-pigułka: biały (główny) albo półprzezroczysty. */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> Palette.Fg.copy(alpha = 0.08f)
        danger -> Palette.Record
        primary -> Palette.Amber
        else -> Palette.Fg.copy(alpha = 0.1f)
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        danger -> Color.White
        primary -> Palette.Ink
        else -> Palette.Fg
    }
    Row(
        modifier
            .reactive(null, enabled, pressScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Ciemne okno dialogowe w stylu aplikacji. */
@Composable
fun DarkDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.PanelHigh)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Fg, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                buttons()
            }
        }
    }
}

/** Lista opcji w oknie dialogowym (np. wybór karty SIM, menu wpisu). */
@Composable
fun ChoiceDialog(title: String, options: List<Pair<ImageVector, String>>, onDismiss: () -> Unit, subtitle: String? = null, onPick: (Int) -> Unit) {
    DarkDialog(title, onDismiss, buttons = { PillButton(tr("Anuluj"), primary = false, onClick = onDismiss) }) {
        if (subtitle != null) Text(subtitle, color = Palette.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // Zwykła lista, bez tła i ramki wokół pozycji.
        Column {
            options.forEachIndexed { i, (icon, label) ->
                ActionRow(icon, label) { onPick(i) }
            }
        }
    }
}

@Composable
fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
    leading: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            imeAction = imeAction,
            capitalization = capitalization,
        ),
        visualTransformation = when {
            password -> PasswordVisualTransformation()
            visualTransformation != null -> visualTransformation
            else -> VisualTransformation.None
        },
        isError = isError,
        leadingIcon = leading?.let { { Icon(it, null, tint = Palette.TextDim) } },
        trailingIcon = trailing,
        modifier = modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Fg,
            unfocusedBorderColor = Palette.Fg.copy(alpha = 0.25f),
            focusedLabelColor = Palette.Fg,
            unfocusedLabelColor = Palette.TextDim,
            cursorColor = Palette.Fg,
            focusedTextColor = Palette.Fg,
            unfocusedTextColor = Palette.Fg,
            errorBorderColor = Palette.Record,
            errorLabelColor = Palette.Record,
            errorCursorColor = Palette.Record,
            errorTextColor = Palette.Fg,
            errorLeadingIconColor = Palette.Record,
        ),
    )
}

/** Zakładka-chip. */
@Composable
fun TabChip(icon: ImageVector?, label: String, selected: Boolean, modifier: Modifier = Modifier, count: Int? = null, compact: Boolean = false, onClick: () -> Unit) {
    // Kompaktowy chip pokazuje samą ikonę; nazwa wysuwa się tylko przy wybranym.
    val showLabel = !compact || selected || icon == null
    Row(
        modifier
            .reactive(label, onClick = onClick)
            .glass(BlockShape, active = selected)
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (showLabel) 11.dp else 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Palette.Ink else Palette.Fg, modifier = Modifier.size(if (compact) 18.dp else 16.dp))
            if (showLabel) Spacer(Modifier.width(6.dp))
        }
        if (showLabel) Text(label, color = if (selected) Palette.Ink else Palette.Fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
        if (count != null && count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                count.toString(),
                color = Palette.Fg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Record).padding(horizontal = 5.dp),
            )
        }
    }
}

/** Poziomy rząd zakładek-chipów z przewijaniem. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Krótka informacja na dole ekranu. */
@Composable
fun MessagePill(text: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Text(
            text ?: "",
            color = Palette.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(Palette.Fg)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Pusty stan listy: ikona, tytuł, opis i opcjonalna akcja. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Palette.Fg.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.TextDim, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Fg)
        Spacer(Modifier.height(4.dp))
        Text(text, fontSize = 13.sp, color = Palette.TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            PillButton(action, onClick = onAction)
        }
    }
}
