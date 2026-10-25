package com.vknxworks.craftyctrl.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

// "Ctrl / Signal": the dark-only look shared by every Ctrl app. Near-black background, flat surfaces with
// hairline borders, one accent colour, Space Grotesk for text and JetBrains Mono for numbers and labels.

/** Every colour of the Signal look. Change [Accent] to recolour the whole app. */
object CtrlColors {
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    val Track = Color(0xFF26262C)
    val CardBorder = Color(0xFF232328)
    val ControlBorder = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val SwitchOff = Color(0xFF2A2A30)
    val PinEmpty = Color(0xFF3A3A41)

    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    val Inactive = Color(0xFF8C8C94)

    val Error = Color(0xFFFF6B6B)
    val Warn = Color(0xFFE5A92E)
}

/** Kept so older settings still load. The app is always dark now; there is no theme choice any more. */
enum class ThemeMode(val key: String, @StringRes val label: Int) {
    SYSTEM("system", R.string.theme_system),
    DARK("dark", R.string.theme_dark),
    LIGHT("light", R.string.theme_light),
    BLACK("black", R.string.theme_black);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Appearance: Signal is dark only, with no Material You colours. Nothing here can be changed. */
object ThemePrefs {
    val mode: ThemeMode get() = ThemeMode.DARK
    val dynamicColor: Boolean get() = false

    /** Older versions stored a theme choice; it's simply ignored now. */
    @Suppress("UNUSED_PARAMETER")
    fun load(context: Context) = Unit

    /** Always dark. */
    @Composable
    fun isDark(): Boolean = true
}

/** Fixed meaning colours for server state. */
@Immutable
data class StatusColors(
    val running: Color,
    val busy: Color,
    val crashed: Color,
    val stopped: Color,
)

private val SignalStatus = StatusColors(
    running = CtrlColors.Accent,
    busy = CtrlColors.Warn,
    crashed = CtrlColors.Error,
    stopped = CtrlColors.Inactive,
)

val LocalStatusColors = staticCompositionLocalOf { SignalStatus }

private val SignalScheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = Color(0xFF241F33),
    onSecondaryContainer = CtrlColors.Accent,
    tertiary = CtrlColors.Muted,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.OnAccent,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF2A1517),
    onErrorContainer = CtrlColors.Error,
    outline = CtrlColors.ControlBorder,
    outlineVariant = CtrlColors.Divider,
    scrim = Color.Black,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Track,
)

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

@Composable
fun CraftyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides SignalStatus) {
        MaterialTheme(colorScheme = SignalScheme, shapes = SignalShapes, typography = SignalTypography, content = content)
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

// The console and editors sit on the deepest black, in JetBrains Mono.
val ConsoleBackground: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Background
val ConsoleText: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Text
val ConsoleWarn: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.busy
val ConsoleError: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Error
val ConsoleDim: Color
    @Composable @ReadOnlyComposable get() = CtrlColors.Muted
