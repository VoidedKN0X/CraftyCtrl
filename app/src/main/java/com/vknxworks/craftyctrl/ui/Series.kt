package com.vknxworks.craftyctrl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.craftyctrl.ui.components.CtrlAvatar
import com.vknxworks.craftyctrl.ui.components.CtrlButton
import com.vknxworks.craftyctrl.ui.components.CtrlLabel
import com.vknxworks.craftyctrl.ui.components.CtrlProgress
import com.vknxworks.craftyctrl.ui.theme.CtrlColors
import com.vknxworks.craftyctrl.ui.theme.CtrlType
import com.vknxworks.craftyctrl.ui.theme.LocalStatusColors

// Building blocks shared with the other Ctrl apps, so the screens look and behave alike (Ctrl / Signal).

/**
 * The profile picture at the top right of the main screens. Tapping it opens the You page, the only
 * way into Settings (like every Ctrl app). Shows the initials of the signed-in user, or the placeholder
 * figure when signed in with an API key (Crafty doesn't host profile pictures of its own).
 */
@Composable
fun ProfileButton(username: String?, onClick: () -> Unit) = CtrlAvatar(username, onClick)

/**
 * Screen header: a mono label over a big Space Grotesk title and one mono line of context, with action
 * buttons on the right. [icon] is kept for older call sites; Signal headers have no icon tile.
 */
@Composable
fun CtrlHeader(
    @Suppress("UNUSED_PARAMETER") icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleColor: Color = CtrlColors.Muted,
    label: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (label != null) CtrlLabel(label)
            Text(title, style = CtrlType.Display, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = CtrlType.MonoSmall, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A card with an optional mono label (see [com.vknxworks.craftyctrl.ui.components.CtrlCard]). */
@Composable
fun CtrlCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) = com.vknxworks.craftyctrl.ui.components.CtrlCard(title, modifier, onClick, content = content)

/** A big number over a small mono label. */
@Composable
fun BigStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CtrlLabel(label)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, color = CtrlColors.Text)
    }
}

/** Label on the left, monospaced value on the right. */
@Composable
fun StatRow(label: String, value: String, warn: Boolean = false) {
    val warnColor = LocalStatusColors.current.busy
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text, modifier = Modifier.weight(1f))
        if (warn) Icon(Icons.Outlined.Warning, null, tint = warnColor, modifier = Modifier.size(16.dp).padding(end = 4.dp))
        Text(
            value,
            style = CtrlType.MonoSmall,
            fontSize = 13.sp,
            color = if (warn) warnColor else CtrlColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A usage meter: label, mono value and a 6 dp bar that turns amber and red as it fills. */
@Composable
fun Meter(label: String, percent: Double?, detail: String? = null) {
    if (percent == null) return
    val p = percent.coerceIn(0.0, 100.0)
    val status = LocalStatusColors.current
    val color = when {
        p >= 90 -> status.crashed
        p >= 75 -> status.busy
        else -> CtrlColors.Text
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
            Text(detail ?: "%.0f %%".format(p), style = CtrlType.MonoSmall, color = CtrlColors.Muted)
        }
        CtrlProgress((p / 100).toFloat(), color = color)
    }
}

/** Centred message for empty or failed screens. */
@Composable
fun EmptyState(title: String, body: String, action: String? = null, icon: ImageVector = Icons.Outlined.CloudOff, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = CtrlColors.Muted)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, textAlign = TextAlign.Center)
        Text(body, color = CtrlColors.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            CtrlButton(onClick = onAction) { Text(action) }
        }
    }
}

/** 1 dp divider between rows inside a card. */
@Composable
fun ItemDivider() = Box(
    Modifier.fillMaxWidth().height(1.dp).background(CtrlColors.CardBorder),
)

