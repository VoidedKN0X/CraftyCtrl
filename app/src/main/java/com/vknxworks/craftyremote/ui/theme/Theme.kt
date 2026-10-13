package com.vknxworks.craftyremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vknxworks.craftyremote.data.ServerState

// Ember-on-graphite palette: warm amber accent, neutral stone surfaces.
private val Dark = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF2B1700),
    primaryContainer = Color(0xFF5A3500),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFD9C3A5),
    onSecondary = Color(0xFF3B2E1C),
    secondaryContainer = Color(0xFF3A342C),
    onSecondaryContainer = Color(0xFFF1E3CF),
    background = Color(0xFF111317),
    onBackground = Color(0xFFE6E2DA),
    surface = Color(0xFF111317),
    onSurface = Color(0xFFE6E2DA),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFA9A49B),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF16191D),
    surfaceContainer = Color(0xFF1A1D22),
    surfaceContainerHigh = Color(0xFF23272D),
    surfaceContainerHighest = Color(0xFF2C3036),
    outline = Color(0xFF4A4D52),
    outlineVariant = Color(0xFF33363B),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF9A5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB3),
    onPrimaryContainer = Color(0xFF2E1800),
    secondary = Color(0xFF6E5B43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E1C9),
    onSecondaryContainer = Color(0xFF261A08),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFBF8F3),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEAE5DD),
    onSurfaceVariant = Color(0xFF5A564F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3ED),
    surfaceContainer = Color(0xFFF2EEE7),
    surfaceContainerHigh = Color(0xFFEAE5DD),
    surfaceContainerHighest = Color(0xFFE3DED5),
    outline = Color(0xFF8A857C),
    outlineVariant = Color(0xFFD6D0C6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun CraftyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}

/** Status colours read well on both light and dark surfaces. */
fun stateColor(state: ServerState?): Color = when (state) {
    ServerState.RUNNING -> Color(0xFF3FB97A)
    ServerState.STARTING, ServerState.UPDATING -> Color(0xFFE5A23A)
    ServerState.CRASHED -> Color(0xFFE5534B)
    ServerState.STOPPED, null -> Color(0xFF8B8F96)
}

// The console stays dark in both themes, like a terminal.
val ConsoleBackground = Color(0xFF0D0F12)
val ConsoleText = Color(0xFFD7D3CB)
val ConsoleWarn = Color(0xFFFFC66D)
val ConsoleError = Color(0xFFFF8A80)
val ConsoleDim = Color(0xFF7D8088)
