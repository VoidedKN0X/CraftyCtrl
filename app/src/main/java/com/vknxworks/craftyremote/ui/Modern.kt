package com.vknxworks.craftyremote.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ring gauge with the value in the middle. [fraction] is 0..1 (null draws an empty track).
 * The ring animates between values so live stats move smoothly.
 */
@Composable
fun RingGauge(
    fraction: Float?,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    stroke: Dp = 9.dp,
) {
    val animated by animateFloatAsState((fraction ?: 0f).coerceIn(0f, 1f), tween(600), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = stroke.toPx()
                val inset = w / 2
                val arcSize = Size(this.size.width - w, this.size.height - w)
                // 270° arc, open at the bottom.
                drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                if (animated > 0f) {
                    drawArc(color, 135f, 270f * animated, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class PillTab(val title: String, val icon: ImageVector)

/** Horizontally scrolling pill tabs; the selected pill fills with the primary container colour. */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(tabs) { i, tab ->
            val isSel = i == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "pillBg",
            )
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "pillFg",
            )
            Surface(onClick = { onSelect(i) }, shape = CircleShape, color = bg, contentColor = fg) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tab.title, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** Large rounded card washed with [accent] from the top-left corner. */
@Composable
fun AccentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val wash = Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.04f), Color.Transparent))
    val body: @Composable () -> Unit = {
        Box(Modifier.background(wash)) { content() }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), color = base, content = body)
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = base, content = body)
    }
}

/** Small rounded label+value chip used for secondary facts (version, port, world size). */
@Composable
fun FactChip(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Fraction helper for "x / max" style gauges. */
fun ratio(value: Number?, max: Number?): Float? {
    val v = value?.toFloat() ?: return null
    val m = max?.toFloat()?.takeIf { it > 0f } ?: return null
    return v / m
}
