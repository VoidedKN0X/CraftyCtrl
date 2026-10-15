package com.vknxworks.craftyremote.ui.theme

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.craftyremote.data.ServerState

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.vknxworks.craftyremote.R

/** Accent choices; "wallpaper" takes the phone's Material You colour (Android 12+). */
enum class Accent(val key: String, val label: String, val color: Color?, val lightColor: Color?) {
    GREEN("green", "Green", Color(0xFF7BD88F), Color(0xFF2F9E55)),
    BLUE("blue", "Blue", Color(0xFF8AB4F8), Color(0xFF2F6FDB)),
    AMBER("amber", "Amber", Color(0xFFE8B04B), Color(0xFFB7791F)),
    PURPLE("purple", "Purple", Color(0xFFC9A7F5), Color(0xFF8A5CD0)),
    WALLPAPER("wallpaper", "Wallpaper", null, null);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/** Light or dark: follow the phone, or always one of them. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Follow system"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** The chosen accent, kept in Compose state so changing it in Settings recolours the app at once. */
object ThemePrefs {
    var accent by mutableStateOf(Accent.GREEN)
    /** The resolved accent colour (the wallpaper colour when that's chosen). */
    var accentColor by mutableStateOf(Color(0xFF7BD88F))
        internal set

    var mode by mutableStateOf(ThemeMode.SYSTEM)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        accent = Accent.of(prefs.getString(KEY, null))
        mode = ThemeMode.of(prefs.getString(KEY_MODE, null))
    }

    fun saveMode(context: Context, m: ThemeMode) {
        mode = m
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString(KEY_MODE, m.key).apply()
    }

    /** Whether the app should be dark right now. */
    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    fun save(context: Context, a: Accent) {
        accent = a
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString(KEY, a.key).apply()
    }

    private const val KEY = "accent"
    private const val KEY_MODE = "theme_mode"
}

// Quiet, near-black ground with off-white text; colour is kept for status and the accent.
private val Ground = Color(0xFF0E0F0E)
private val Ink = Color(0xFFEDEDEA)
private val Muted = Color(0xFF8A8C88)
private val Hairline = Color(0xFF232523)

// The same idea inverted: warm off-white ground, near-black text.
private val LGround = Color(0xFFFAFAF8)
private val LInk = Color(0xFF141514)
private val LMuted = Color(0xFF666864)
private val LHairline = Color(0xFFE4E5E2)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = LInk,
    onPrimary = LGround,
    primaryContainer = Color(0xFFE9EAE7),
    onPrimaryContainer = LInk,
    secondary = accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEEEB),
    onSecondaryContainer = LInk,
    tertiary = Color(0xFF9A6410),
    onTertiary = Color.White,
    background = LGround,
    onBackground = LInk,
    surface = LGround,
    onSurface = LInk,
    surfaceVariant = Color(0xFFEFF0ED),
    onSurfaceVariant = LMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = LInk,
    inverseOnSurface = LGround,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F3),
    surfaceContainer = Color(0xFFF2F2EF),
    surfaceContainerHigh = Color(0xFFECEDEA),
    surfaceContainerHighest = Color(0xFFE4E5E2),
    outline = Color(0xFFC9CBC6),
    outlineVariant = LHairline,
    error = Color(0xFFC0362F),
    onError = Color.White,
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF5C1210),
)

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = Ink,
    onPrimary = Ground,
    primaryContainer = Color(0xFF232523),
    onPrimaryContainer = Ink,
    secondary = accent,
    onSecondary = Ground,
    secondaryContainer = Color(0xFF1F211F),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFFE8B04B),
    onTertiary = Ground,
    background = Ground,
    onBackground = Ink,
    surface = Ground,
    onSurface = Ink,
    surfaceVariant = Color(0xFF1B1C1B),
    onSurfaceVariant = Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = Ink,
    inverseOnSurface = Ground,
    surfaceContainerLowest = Color(0xFF0A0B0A),
    surfaceContainerLow = Color(0xFF121312),
    surfaceContainer = Color(0xFF151615),
    surfaceContainerHigh = Color(0xFF1B1C1B),
    surfaceContainerHighest = Color(0xFF232523),
    outline = Color(0xFF3A3C39),
    outlineVariant = Hairline,
    error = Color(0xFFF2938F),
    onError = Ground,
    errorContainer = Color(0xFF3A1C1B),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun CraftyTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = ThemePrefs.isDark()
    val a = ThemePrefs.accent
    val accent = (if (dark) a.color else a.lightColor)
        ?: when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (dark) Accent.GREEN.color!! else Accent.GREEN.lightColor!!
            dark -> dynamicDarkColorScheme(context).primary
            else -> dynamicLightColorScheme(context).primary
        }
    if (ThemePrefs.accentColor != accent) ThemePrefs.accentColor = accent
    MaterialTheme(
        colorScheme = if (dark) darkScheme(accent) else lightScheme(accent),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun geist(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Geist for text, Geist Mono for numbers and code (variable fonts, bundled at build time). */
val Geist = FontFamily(geist(R.font.geist, 400), geist(R.font.geist, 500), geist(R.font.geist, 600), geist(R.font.geist, 700))
val GeistMono = FontFamily(geist(R.font.geist_mono, 400), geist(R.font.geist_mono, 500), geist(R.font.geist_mono, 600))

/** Gentle corners; most surfaces are flat and separated by hairlines. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Geist throughout, medium-weight headings with slightly tight tracking. */
private val AppTypography = Typography().run {
    fun TextStyle.g(weight: FontWeight = FontWeight.Normal, tracking: Float? = null) =
        copy(fontFamily = Geist, fontWeight = weight, letterSpacing = tracking?.sp ?: letterSpacing)
    copy(
        displayLarge = displayLarge.g(FontWeight.Medium, -1f),
        displayMedium = displayMedium.g(FontWeight.Medium, -0.8f),
        displaySmall = displaySmall.g(FontWeight.Medium, -0.6f),
        headlineLarge = headlineLarge.g(FontWeight.Medium, -0.6f),
        headlineMedium = headlineMedium.g(FontWeight.Medium, -0.5f),
        headlineSmall = headlineSmall.g(FontWeight.Medium, -0.3f),
        titleLarge = titleLarge.g(FontWeight.Medium, -0.2f),
        titleMedium = titleMedium.g(FontWeight.Medium),
        titleSmall = titleSmall.g(FontWeight.Medium),
        bodyLarge = bodyLarge.g(),
        bodyMedium = bodyMedium.g(),
        bodySmall = bodySmall.g(),
        labelLarge = labelLarge.g(FontWeight.Medium),
        labelMedium = labelMedium.g(FontWeight.Medium),
        labelSmall = labelSmall.g(FontWeight.Medium),
    )
}

/** Status colours read well on both light and dark surfaces. */
fun stateColor(state: ServerState?): Color = when (state) {
    ServerState.RUNNING -> ThemePrefs.accentColor
    ServerState.STARTING, ServerState.UPDATING -> Color(0xFFE8B04B)
    ServerState.CRASHED -> Color(0xFFF2777A)
    ServerState.STOPPED, null -> Color(0xFF8A8C88)
}

// Console and editor colours come from the active theme.
val ConsoleBackground: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerLowest
val ConsoleText: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
val ConsoleWarn: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val ConsoleError: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
val ConsoleDim: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant
