package com.vknxworks.craftyremote.ui

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

/** One line series. Null values leave a gap. [maxY] fixes the top of the scale (e.g. 100 for %). */
@Composable
fun LineChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    maxY: Float? = null,
    height: Dp = 120.dp,
    fill: Boolean = true,
    gridLines: Int = 3,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height)) {
        val present = values.filterNotNull()
        if (values.size < 2 || present.isEmpty()) return@Canvas
        val top = (maxY ?: (present.max() * 1.15f)).coerceAtLeast(1f)
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

@Composable
fun ChartCard(
    title: String,
    current: String,
    values: List<Float?>,
    color: Color,
    maxY: Float?,
    footer: String? = null,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(current, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
            if (values.count { it != null } >= 2) {
                LineChart(values, color, maxY = maxY)
            } else {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Collecting data…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            footer?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val p = ((percent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (p > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
    }
}

// Series colours, readable in both themes.
val ChartCpu = Color(0xFFFFB547)
val ChartMem = Color(0xFF6CB6FF)
val ChartPlayers = Color(0xFF3FB97A)
