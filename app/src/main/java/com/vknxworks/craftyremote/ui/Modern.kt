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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import com.vknxworks.craftyremote.ui.theme.GeistMono

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

/** Text tabs with an underline under the selected one, over a hairline; scrolls sideways. */
@Composable
fun PillTabs(tabs: List<PillTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(selected) { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    Box(modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.outlineVariant)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            itemsIndexed(tabs) { i, tab ->
                val isSel = i == selected
                val fg by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabFg",
                )
                val line by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "tabLine",
                )
                Box(
                    Modifier
                        .clickable { onSelect(i) }
                        .padding(horizontal = 12.dp),
                ) {
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fg,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
                        )
                        Box(Modifier.fillMaxWidth().height(2.dp).background(line))
                    }
                }
            }
        }
    }
}

/** A flat block; [accent] is kept for call sites but only status dots carry colour now. */
@Composable
fun AccentCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, color = Color.Transparent, content = content)
    } else {
        Surface(modifier = modifier, color = Color.Transparent, content = content)
    }
}

/** A small status dot. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** Uppercase caption used for labels over numbers and section headings. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Caption, a big monospaced value and an optional line under it. */
@Composable
fun BigMetric(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, bar: Float? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption(label)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = GeistMono, maxLines = 1)
        bar?.let { HairBar(it) }
        sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}

/** Label on the left, monospaced value on the right, over a hairline. */
@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = GeistMono, maxLines = 1)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Kept for older call sites: now a plain label/value pair. */
@Composable
fun FactChip(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    BigMetric(label, value, modifier = modifier)
}

/** Fraction helper for "x / max" style gauges. */
fun ratio(value: Number?, max: Number?): Float? {
    val v = value?.toFloat() ?: return null
    val m = max?.toFloat()?.takeIf { it > 0f } ?: return null
    return v / m
}
