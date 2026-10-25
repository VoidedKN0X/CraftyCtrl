@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.vknxworks.craftyctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.ui.IslandTab
import com.vknxworks.craftyctrl.ui.pressScale
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import com.vknxworks.craftyctrl.ui.theme.SpaceGrotesk
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// The "Ctrl / Signal" building blocks shared by every Ctrl app: top bars, cards, hero card, list rows,
// bottom bar, switch, buttons, text fields, dialogs and sheets.

val CardShape = RoundedCornerShape(22.dp)
val HeroShape = RoundedCornerShape(26.dp)
val ControlShape = RoundedCornerShape(16.dp)
val SquareShape = RoundedCornerShape(14.dp)
val BadgeShape = RoundedCornerShape(10.dp)
private val DialogShape = RoundedCornerShape(26.dp)
private val SheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

/** The 1 dp hairline round every card. */
val CtrlCardBorder = BorderStroke(1.dp, CtrlColors.CardBorder)

/** Room the floating bottom bar takes above the navigation bar (64 bar + 28 margin + a little air). */
val CtrlBottomBarSpace: Dp = 100.dp

// ---------------------------------------------------------------- text

/** "CRAFTY/CTRL" with the slash in the accent colour. */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.Accent)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** Section label: JetBrains Mono 11, upper case, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// ---------------------------------------------------------------- top bars

/** A 44 dp rounded-square button with a hairline border, used for back, close and header actions. */
@Composable
fun CtrlIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.Text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(SquareShape)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlBorder, SquareShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint) }
}

@Composable
fun CtrlBackButton(onClick: () -> Unit, enabled: Boolean = true) {
    CtrlIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick, enabled = enabled)
}

/**
 * The profile picture at the top right: a 44 dp rounded square with initials (or the placeholder figure),
 * and a small accent square at the corner while connected. Opens the profile page.
 */
@Composable
fun CtrlAvatar(username: String?, onClick: () -> Unit, connected: Boolean = true) {
    val label = stringResource(R.string.account_and_settings)
    val initials = username?.trim()?.takeIf { it.isNotEmpty() }?.let { initialsOf(it) }
    Box(Modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(SquareShape)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlBorder, SquareShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (initials == null) {
                Icon(Icons.Outlined.AccountCircle, null, Modifier.size(22.dp), tint = CtrlColors.Text)
            } else {
                Text(initials, fontFamily = SpaceGrotesk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CtrlColors.Text)
            }
        }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(3.dp, 3.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/** Up to two initials: "axel" -> "AX", "Axel Smith" -> "AS". */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
        else -> name.trim().take(2).uppercase()
    }
}

/** Main-screen top bar: the wordmark on the left, actions and the profile picture on the right. No fill. */
@Composable
fun CtrlMainTopBar(
    name: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark(name, Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Sub-screen top bar: a square back button and the title in Space Grotesk, on the background (no fill). */
@Composable
fun CtrlTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CtrlColors.Background)
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon()
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalContentColor provides CtrlColors.Text) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            }
        }
        CompositionLocalProvider(LocalContentColor provides CtrlColors.Text) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

// ---------------------------------------------------------------- cards

/** Secondary card: surface fill, 1 dp border, 22 dp corners. An optional mono label heads it. */
@Composable
fun CtrlCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) CtrlLabel(title)
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, shape = CardShape, colors = colors, border = CtrlCardBorder, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(shape = CardShape, colors = colors, border = CtrlCardBorder, modifier = modifier.fillMaxWidth()) { body() }
    }
}

/**
 * The hero card, for the one most important thing on a screen: accent fill, near-black content,
 * a mono label row on top and (usually) a giant number below.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    trailingLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(HeroShape).background(CtrlColors.Accent).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides CtrlColors.OnAccent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold), color = CtrlColors.OnAccent, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trailingLabel != null) {
                    Text(trailingLabel.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp), color = CtrlColors.OnAccent, maxLines = 1)
                }
            }
            content()
        }
    }
}

/** The giant readout: "23" with a smaller "%" (or "/ 20") after it. */
@Composable
fun HeroNumber(value: String, unit: String? = null, color: Color = LocalContentColor.current) {
    Text(
        buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(SpanStyle(fontSize = 28.sp, letterSpacing = 0.sp)) { append(unit) }
        },
        style = CtrlType.Hero,
        color = color,
        maxLines = 1,
    )
}

/** Hero bar sparkline: the newest few bars at full strength. */
@Composable
fun HeroBars(values: List<Float>, max: Float = 100f, modifier: Modifier = Modifier, color: Color = CtrlColors.OnAccent, count: Int = 24) {
    val shown = values.takeLast(count)
    Row(modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        repeat(count - shown.size) { Box(Modifier.weight(1f).fillMaxHeight(0.06f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.18f))) }
        shown.forEachIndexed { i, v ->
            val f = (v / max).coerceIn(0.06f, 1f)
            val recent = i >= shown.size - 4
            Box(Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = if (recent) 1f else 0.55f)))
        }
    }
}

/** A metric card: mono label, a 30 sp value with a muted unit, and an optional progress bar. */
@Composable
fun CtrlMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    fraction: Float? = null,
    barColor: Color = CtrlColors.Text,
) {
    Column(
        modifier.clip(CardShape).background(CtrlColors.Surface).border(CtrlCardBorder, CardShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CtrlLabel(label)
        Text(
            buildAnnotatedString {
                append(value)
                if (unit != null) withStyle(SpanStyle(fontSize = 14.sp, color = CtrlColors.Muted, letterSpacing = 0.sp)) { append(unit) }
            },
            style = CtrlType.Metric,
            color = CtrlColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (fraction != null) CtrlProgress(fraction, color = barColor)
    }
}

/** 6 dp progress bar with 3 dp corners. */
@Composable
fun CtrlProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = CtrlColors.Text) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "progress")
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(CtrlColors.Track)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
    }
}

/** 8 dp status square with 2 dp corners. */
@Composable
fun StatusSquare(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(2.dp)).background(color))
}

/**
 * A plain list row: muted mono meta on the left, the title (and an optional line under it), then a mono
 * status in the accent colour (active) or grey, with a status square. A 1 dp divider runs along the top.
 */
@Composable
fun CtrlListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    subtitle: String? = null,
    status: String? = null,
    statusColor: Color = CtrlColors.Inactive,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(CtrlColors.Divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (meta != null) {
                Text(meta, style = CtrlType.MonoSmall, color = CtrlColors.Muted, maxLines = 1, modifier = Modifier.widthIn(min = 30.dp, max = 72.dp), overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = CtrlType.MonoSmall, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status != null) {
                Text(status.uppercase(), style = CtrlType.MonoStatus, color = statusColor, maxLines = 1)
                StatusSquare(statusColor)
            }
        }
    }
}

// ---------------------------------------------------------------- bottom bar

/**
 * Floating bottom bar: 64 dp tall, 20 dp from the sides and 28 dp above the navigation bar, 22 dp corners,
 * the surface colour at 92 %, 1 dp border. The selected tab is an accent rounded rectangle with icon and
 * label; the others are icons only.
 */
@Composable
fun CtrlBottomBar(
    tabs: List<IslandTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(shape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = CtrlColors.Background,
                                tints = listOf(HazeTint(CtrlColors.Surface.copy(alpha = 0.92f))),
                                blurRadius = 20.dp,
                                noiseFactor = 0f,
                            ),
                        )
                    } else {
                        Modifier.background(CtrlColors.Surface.copy(alpha = 0.92f))
                    },
                )
                .border(1.dp, CtrlColors.ControlBorder, shape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEach { t ->
                val isSel = t.index == selected
                val bg by animateColorAsState(if (isSel) CtrlColors.Accent else Color.Transparent, label = "tabBg")
                val tint by animateColorAsState(if (isSel) CtrlColors.OnAccent else CtrlColors.Muted, label = "tabTint")
                val src = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .then(if (isSel) Modifier.weight(1f, fill = false) else Modifier.width(46.dp))
                        .height(48.dp)
                        .pressScale(src, 0.9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .clickable(src, indication = null, role = Role.Tab) {
                            if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(t.index)
                        }
                        .semantics { contentDescription = t.label }
                        .padding(horizontal = if (isSel) 14.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BadgedBox(badge = {
                        if (t.badge > 0) Badge(containerColor = CtrlColors.Error, contentColor = CtrlColors.OnAccent) { Text(if (t.badge > 99) "99+" else "${t.badge}") }
                    }) {
                        Icon(t.icon, null, Modifier.size(if (isSel) 18.dp else 20.dp), tint = tint)
                    }
                    if (isSel) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.label,
                            fontFamily = SpaceGrotesk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- controls

/**
 * Rounded-rectangle switch: 52 x 32 dp track with 10 dp corners and 4 dp padding, a 24 dp knob with 7 dp corners.
 * On: accent track, near-black knob. Off: dark track, grey knob. Announced as a switch.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobX by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium), label = "knob")
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.Inactive, label = "knobColor")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(52.dp, 32.dp)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(10.dp))
                .background(track)
                .padding(4.dp),
        ) {
            Box(Modifier.offset(x = knobX).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
        }
    }
}

/** Primary button: accent fill, near-black text, 16 dp corners. */
@Composable
fun CtrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.OnAccent,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Secondary button: surface fill with a 1 dp border, 16 dp corners. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ControlShape,
        colors = colors ?: ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Inactive,
        ),
        border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Text field: surface fill, 1 dp border, 16 dp corners; accent border and cursor while focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    // Kept for older call sites; Signal text fields always use 16 dp corners (no pills).
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            errorContainerColor = CtrlColors.Surface,
            focusedBorderColor = CtrlColors.Accent,
            unfocusedBorderColor = CtrlColors.ControlBorder,
            disabledBorderColor = CtrlColors.CardBorder,
            cursorColor = CtrlColors.Accent,
            focusedLabelColor = CtrlColors.Accent,
            unfocusedLabelColor = CtrlColors.Muted,
            focusedLeadingIconColor = CtrlColors.Muted,
            unfocusedLeadingIconColor = CtrlColors.Muted,
            focusedTrailingIconColor = CtrlColors.Muted,
            unfocusedTrailingIconColor = CtrlColors.Muted,
            focusedPlaceholderColor = CtrlColors.Inactive,
            unfocusedPlaceholderColor = CtrlColors.Inactive,
        ),
    )
}

// ---------------------------------------------------------------- dialogs and sheets

/** Dialog: surface fill, 26 dp corners, 1 dp border. Same slots as Material's AlertDialog. */
@Composable
fun CtrlDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.CardBorder, DialogShape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = DialogShape,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.Accent,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** Bottom sheet: surface fill, 26 dp top corners, 1 dp border. */
@Composable
fun CtrlSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = CtrlColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier.border(1.dp, CtrlColors.CardBorder, SheetShape),
        shape = SheetShape,
        containerColor = containerColor,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        content = content,
    )
}
