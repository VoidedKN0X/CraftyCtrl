package com.vknxworks.craftyctrl.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.craftyctrl.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vknxworks.craftyctrl.data.ServerState

// The same theme setup as the other Ctrl apps: Material You by default, a brand fallback,
// Follow system / Dark / Light / Pure black, the system font and the shared corner shapes.

/** The theme choices every Ctrl app offers: follow the phone, always dark or light, or pure black. */
enum class ThemeMode(val key: String, @StringRes val label: Int) {
    SYSTEM("system", R.string.theme_system),
    DARK("dark", R.string.theme_dark),
    LIGHT("light", R.string.theme_light),
    BLACK("black", R.string.theme_black);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Appearance settings, kept in Compose state so changes recolour the app at once. */
object ThemePrefs {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var dynamicColor by mutableStateOf(true)
        private set

    /** True black backgrounds (the "Pure black" theme). */
    val pureBlack: Boolean get() = mode == ThemeMode.BLACK

    private fun prefs(c: Context) = c.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        // Before 2.5.2, pure black was a separate switch: Dark with it on is now the Pure black theme.
        if (p.contains(KEY_BLACK)) {
            val wasBlack = p.getBoolean(KEY_BLACK, false)
            val edit = p.edit().remove(KEY_BLACK)
            if (wasBlack && ThemeMode.of(p.getString(KEY_MODE, null)) == ThemeMode.DARK) edit.putString(KEY_MODE, ThemeMode.BLACK.key)
            edit.apply()
        }
        mode = ThemeMode.of(p.getString(KEY_MODE, null))
        dynamicColor = p.getBoolean(KEY_DYNAMIC, true)
    }

    fun saveMode(context: Context, m: ThemeMode) {
        mode = m
        prefs(context).edit().putString(KEY_MODE, m.key).apply()
    }

    fun saveDynamic(context: Context, on: Boolean) {
        dynamicColor = on
        prefs(context).edit().putBoolean(KEY_DYNAMIC, on).apply()
    }

    /** Whether the app should be dark right now. */
    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }

    private const val KEY_MODE = "theme_mode"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_BLACK = "pure_black"
}

/** Fixed meaning colours for server state, tuned per light/dark so they stay legible. */
@Immutable
data class StatusColors(
    val running: Color,
    val busy: Color,
    val crashed: Color,
    val stopped: Color,
)

private val DarkStatus = StatusColors(
    running = Color(0xFF34C77B),
    busy = Color(0xFFE5A92E),
    crashed = Color(0xFFE5534B),
    stopped = Color(0xFF8E9099),
)
private val LightStatus = StatusColors(
    running = Color(0xFF15934F),
    busy = Color(0xFFB57A00),
    crashed = Color(0xFFC62828),
    stopped = Color(0xFF74777F),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

// Brand fallback when Material You is off: the blue of the app icon.
private val BrandDark = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF0A2287),
    primaryContainer = Color(0xFF2A3DA0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFE3BADA),
    onTertiary = Color(0xFF43273F),
    tertiaryContainer = Color(0xFF5B3D57),
    onTertiaryContainer = Color(0xFFFFD6F7),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
)

private val BrandLight = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF77536D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F1),
    onTertiaryContainer = Color(0xFF2D1228),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)

@Composable
fun CraftyTheme(content: @Composable () -> Unit) {
    val dark = ThemePrefs.isDark()
    val ctx = LocalContext.current
    var scheme = when {
        ThemePrefs.dynamicColor -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> BrandDark
        else -> BrandLight
    }
    if (dark && ThemePrefs.pureBlack) scheme = scheme.pureBlack()
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
    }
}

/** Monospaced text for numbers, code and logs. */
val Mono: FontFamily = FontFamily.Monospace

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

// Console and editor colours come from the active theme.
val ConsoleBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLowest
val ConsoleText: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val ConsoleWarn: Color
    @Composable @ReadOnlyComposable get() = LocalStatusColors.current.busy
val ConsoleError: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val ConsoleDim: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
