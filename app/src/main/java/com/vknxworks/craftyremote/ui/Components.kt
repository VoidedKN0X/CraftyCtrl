package com.vknxworks.craftyremote.ui

import com.vknxworks.craftyremote.ui.theme.GeistMono
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.craftyremote.data.ServerState
import com.vknxworks.craftyremote.ui.theme.stateColor
import kotlinx.coroutines.delay

/** The in-app refresh interval from Settings, re-read whenever a screen comes back into view. */
@Composable
fun rememberRefreshMs(): Long {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ms by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableLongStateOf(com.vknxworks.craftyremote.data.AppSettings(context).liveRefreshSeconds * 1000L)
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        ms = com.vknxworks.craftyremote.data.AppSettings(context).liveRefreshSeconds * 1000L
        onPauseOrDispose { }
    }
    return ms
}

/** Runs [block] every [intervalMs] while the screen is visible; pauses in the background. */
@Composable
fun PollWhileVisible(key: Any?, intervalMs: Long, block: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(block)
    LaunchedEffect(key, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                latest()
                delay(intervalMs)
            }
        }
    }
}

@Composable
fun StatusDot(state: ServerState?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(stateColor(state)),
    )
}

@Composable
fun StatusPill(state: ServerState?) {
    // Just a dot and a word: the colour carries the state, the text stays quiet.
    Row(
        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(state, Modifier.size(7.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            state?.label ?: "Unknown",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
