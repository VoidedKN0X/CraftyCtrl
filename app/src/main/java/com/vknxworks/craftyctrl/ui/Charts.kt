package com.vknxworks.craftyctrl.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import com.vknxworks.craftyctrl.ui.theme.Mono
import androidx.compose.ui.res.stringResource
import com.vknxworks.craftyctrl.R

/** One line series. Null values leave a gap. [maxY] fixes the top of the scale (e.g. 100 for %). */
@Composable
fun LineChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    maxY: Float? = null,
    height: Dp = 72.dp,
    fill: Boolean = false,
    gridLines: Int = 0,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val present = values.filterNotNull()
        if (values.size < 2 || present.isEmpty()) return@Canvas
        val top = (maxY ?: (present.max() * 1.15f)).coerceAtLeast(1f)
        // Baseline hairline.
        drawLine(grid, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
        val stepX = size.width / (values.size - 1)
        fun y(v: Float) = size.height - (v / top).coerceIn(0f, 1f) * size.height

        for (i in 1..gridLines) {
            val gy = size.height * i / (gridLines + 1)
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }

        // Build one path per continuous run of values.
        var line: Path? = null
        var area: Path? = null
        var runStartX = 0f
        var lastX = 0f
        val lines = mutableListOf<Path>()
        val areas = mutableListOf<Path>()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            if (v == null) {
                line?.let { lines += it }
                area?.let { it.lineTo(lastX, size.height); it.lineTo(runStartX, size.height); it.close(); areas += it }
                line = null; area = null
                return@forEachIndexed
            }
            if (line == null) {
                line = Path().apply { moveTo(x, y(v)) }
                area = Path().apply { moveTo(x, size.height); lineTo(x, y(v)) }
                runStartX = x
            } else {
                line!!.lineTo(x, y(v))
                area!!.lineTo(x, y(v))
            }
            lastX = x
        }
        line?.let { lines += it }
        area?.let { it.lineTo(lastX, size.height); it.lineTo(runStartX, size.height); it.close(); areas += it }

        if (fill) {
            val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)))
            areas.forEach { drawPath(it, brush) }
        }
        lines.forEach {
            drawPath(it, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A chart card: label and current value, the line, then min / avg / max. */
@Composable
fun ChartCard(
    title: String,
    current: String,
    values: List<Float?>,
    color: Color,
    maxY: Float?,
    footer: String? = null,
    format: (Float) -> String = { "%.0f".format(it) },
) {
    CtrlCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(current, style = MaterialTheme.typography.titleMedium, fontFamily = Mono)
        }
        val present = values.filterNotNull()
        if (present.size >= 2) {
            LineChart(values, color, maxY = maxY, height = 110.dp, fill = true, gridLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(R.string.chart_min to present.min(), R.string.chart_avg to present.average().toFloat(), R.string.chart_max to present.max()).forEach { (k, v) ->
                    Text(stringResource(k, format(v)), style = MaterialTheme.typography.labelSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.collecting_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        footer?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Labelled usage bar, e.g. a disk. */
@Composable
fun UsageBar(label: String, percent: Double?, detail: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                listOfNotNull(detail, percent?.let { "%.0f%%".format(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = Mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val p = ((percent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
        HairBar(p, if (p > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

/** A 2 dp track with a filled part, used instead of thick progress bars and ring gauges. */
@Composable
fun HairBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(track)
        drawRect(color, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)))
    }
}

// Each series has its own Material colour role.
val ChartCpu: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary
val ChartMem: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary
val ChartPlayers: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.secondary
