package com.vknxworks.craftyctrl.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.craftyctrl.R
import com.vknxworks.craftyctrl.data.ServerState

// "Ctrl / Signal": the look shared by every Ctrl app. Near-black background, flat surfaces with
// hairline borders, one accent colour, Space Grotesk for text and JetBrains Mono for numbers and labels.

/** One Signal palette (dark or light). Read the current one through [CtrlColors] or [LocalCtrlColors]. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val track: Color,
    val cardBorder: Color,
    val controlBorder: Color,
    val divider: Color,
    val switchOff: Color,
    val pinEmpty: Color,
    val text: Color,
    val muted: Color,
    val inactive: Color,
    val error: Color,
    val onError: Color,
    val warn: Color,
    /** The accent for text and icons on this background (the fill colour is too pale on white). */
    val accentText: Color,
)

val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * Every colour of the Signal look. [Accent] (the fill used by hero cards, primary buttons, the selected tab,
 * switches and PIN dots) is the same in both palettes; change it here to recolour the whole app.
 * The other colours follow the device's dark or light setting.
 */
object CtrlColors {
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        track = Color(0xFF26262C),
        cardBorder = Color(0xFF232328),
        controlBorder = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        switchOff = Color(0xFF2A2A30),
        pinEmpty = Color(0xFF3A3A41),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        inactive = Color(0xFF8C8C94),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        warn = Color(0xFFE5A92E),
        accentText = Accent,
    )

    val Light = CtrlPalette(
        isDark = false,
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        track = Color(0xFFE2E2DC),
        cardBorder = Color(0xFFDEDED7),
        controlBorder = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        switchOff = Color(0xFFDCDCD5),
        pinEmpty = Color(0xFFC9C9C1),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        inactive = Color(0xFF74747C),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        warn = Color(0xFFAD7400),
        accentText = Color(0xFF6A3BDB),
    )

    private val p: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val AccentText: Color @Composable @ReadOnlyComposable get() = p.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = p.background
    val Surface: Color @Composable @ReadOnlyComposable get() = p.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = p.raised
    val Track: Color @Composable @ReadOnlyComposable get() = p.track
    val CardBorder: Color @Composable @ReadOnlyComposable get() = p.cardBorder
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = p.controlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = p.divider
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = p.switchOff
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = p.pinEmpty
    val Text: Color @Composable @ReadOnlyComposable get() = p.text
    val Muted: Color @Composable @ReadOnlyComposable get() = p.muted
    val Inactive: Color @Composable @ReadOnlyComposable get() = p.inactive
    val Error: Color @Composable @ReadOnlyComposable get() = p.error
    val Warn: Color @Composable @ReadOnlyComposable get() = p.warn
}

/** Kept so older settings still load. The app follows the device theme; there is no theme choice any more. */
enum class ThemeMode(val key: String, @StringRes val label: Int) {
    SYSTEM("system", R.string.theme_system),
    DARK("dark", R.string.theme_dark),
    LIGHT("light", R.string.theme_light),
    BLACK("black", R.string.theme_black);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Appearance: Signal follows the device's dark or light setting, with no Material You colours and no in-app choice. */
object ThemePrefs {
    val mode: ThemeMode get() = ThemeMode.DARK
    val dynamicColor: Boolean get() = false

    /** Older versions stored a theme choice; it's simply ignored now. */
    @Suppress("UNUSED_PARAMETER")
    fun load(context: Context) = Unit

    /** Dark when the device is in dark mode. */
    @Composable
    fun isDark(): Boolean = isSystemInDarkTheme()
}

/** Fixed meaning colours for server state. */
@Immutable
data class StatusColors(
    val running: Color,
    val busy: Color,
    val crashed: Color,
    val stopped: Color,
)

private val DarkStatus = StatusColors(
    running = CtrlColors.Dark.accentText,
    busy = CtrlColors.Dark.warn,
    crashed = CtrlColors.Dark.error,
    stopped = CtrlColors.Dark.inactive,
)
private val LightStatus = StatusColors(
    running = CtrlColors.Light.accentText,
    busy = CtrlColors.Light.warn,
    crashed = CtrlColors.Light.error,
    stopped = CtrlColors.Light.inactive,
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private fun signalScheme(c: CtrlPalette) = if (c.isDark) {
    darkColorScheme(
        primary = CtrlColors.Accent,
        onPrimary = CtrlColors.OnAccent,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = c.text,
        onSecondary = CtrlColors.OnAccent,
        secondaryContainer = Color(0xFF241F33),
        onSecondaryContainer = c.accentText,
        tertiary = c.accentText,
        onTertiary = CtrlColors.OnAccent,
        tertiaryContainer = c.raised,
        onTertiaryContainer = c.text,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.raised,
        onSurfaceVariant = c.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        error = c.error,
        onError = c.onError,
        errorContainer = Color(0xFF2A1517),
        onErrorContainer = c.error,
        outline = c.controlBorder,
        outlineVariant = c.divider,
        scrim = Color.Black,
        surfaceBright = c.raised,
        surfaceDim = c.background,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.raised,
        surfaceContainerHighest = c.track,
    )
} else {
    // On light backgrounds Material's own text-coloured accent (text buttons, checkboxes, spinners) uses the
    // deeper violet; the pale accent stays the fill of Signal's own hero cards, buttons, tabs and switches.
    lightColorScheme(
        primary = c.accentText,
        onPrimary = Color.White,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = c.text,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEAE1FF),
        onSecondaryContainer = Color(0xFF4A23A8),
        tertiary = c.accentText,
        onTertiary = Color.White,
        tertiaryContainer = c.raised,
        onTertiaryContainer = c.text,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.raised,
        onSurfaceVariant = c.muted,
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF141417),
        inverseOnSurface = Color(0xFFF4F4F2),
        error = c.error,
        onError = c.onError,
        errorContainer = Color(0xFFFBE4E4),
        onErrorContainer = Color(0xFFA82424),
        outline = c.controlBorder,
        outlineVariant = c.divider,
        scrim = Color.Black,
        surfaceBright = c.surface,
        surfaceDim = c.raised,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.raised,
        surfaceContainerHighest = c.track,
    )
}

private val DarkScheme = signalScheme(CtrlColors.Dark)
private val LightScheme = signalScheme(CtrlColors.Light)

// Text fields and menus 16, chips and badges 10, cards 22, dialogs, sheets and hero cards 26.
private val SignalShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@OptIn(ExperimentalTextApi::class)
private fun spaceGroteskVariable(weight: Int) = Font(
    R.font.space_grotesk_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk (SIL OFL 1.1), bundled: all UI text and headings. */
val SpaceGrotesk: FontFamily = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    spaceGroteskVariable(600),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** JetBrains Mono (SIL OFL 1.1), bundled: numbers, data readouts, section labels, logs and the wordmark. */
val JetBrainsMono: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

/** Monospaced text for numbers, code and logs. */
val Mono: FontFamily = JetBrainsMono

private fun sg(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val SignalTypography = Typography(
    displayLarge = sg(56, 56, FontWeight.Bold, -0.04),
    displayMedium = sg(45, 48, FontWeight.Bold, -0.04),
    displaySmall = sg(36, 40, FontWeight.Bold, -0.035),
    headlineLarge = sg(40, 44, FontWeight.Bold, -0.035),
    headlineMedium = sg(28, 34, FontWeight.Bold, -0.025),
    headlineSmall = sg(24, 30, FontWeight.SemiBold, -0.02),
    titleLarge = sg(20, 26, FontWeight.SemiBold, -0.01),
    titleMedium = sg(18, 24, FontWeight.SemiBold),
    titleSmall = sg(15, 20, FontWeight.SemiBold),
    bodyLarge = sg(16, 22, FontWeight.Normal),
    bodyMedium = sg(14, 20, FontWeight.Normal),
    bodySmall = sg(12, 16, FontWeight.Normal),
    labelLarge = sg(14, 20, FontWeight.SemiBold),
    labelMedium = sg(12, 16, FontWeight.Medium),
    labelSmall = sg(11, 16, FontWeight.Medium),
)

/** The Signal text styles that have no Material slot. */
object CtrlType {
    /** Section labels: JetBrains Mono 11, upper case, wide tracking (callers upper-case the text). */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** The "CRAFTY/CTRL" wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Giant readout on hero cards. */
    val Hero = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** Big screen title (56). */
    val Display = TextStyle(fontFamily = SpaceGrotesk, fontSize = 56.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.04).em)
    /** Readout in a metric card. */
    val Metric = TextStyle(fontFamily = SpaceGrotesk, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    /** Small mono data next to rows. */
    val MonoSmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Mono status words in lists. */
    val MonoStatus = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
}

/** Signal Dark or Signal Light, following the device's dark-mode setting. */
@Composable
fun CraftyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(
        LocalCtrlColors provides if (dark) CtrlColors.Dark else CtrlColors.Light,
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, shapes = SignalShapes, typography = SignalTypography, content = content)
    }
}

/** Colour of a server state's dot. */
@Composable
@ReadOnlyComposable
fun stateColor(state: ServerState?): Color {
    val s = LocalStatusColors.current
    return when (state) {
        ServerState.RUNNING -> s.running
        ServerState.STARTING, ServerState.UPDATING -> s.busy
        ServerState.CRASHED -> s.crashed
        ServerState.STOPPED, null -> s.stopped
    }
}

// The console, log viewer and file editor stay on the deepest black in both modes, so log colours read the same.
val ConsoleBackground: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Dark.background
val ConsoleText: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Dark.text
val ConsoleWarn: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Dark.warn
val ConsoleError: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Dark.error
val ConsoleDim: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Dark.muted
