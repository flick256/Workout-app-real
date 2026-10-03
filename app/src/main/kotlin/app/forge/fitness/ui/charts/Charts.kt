package app.forge.fitness.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.forge.domain.analytics.HeatCell
import app.forge.fitness.ui.theme.Spacing
import app.forge.fitness.ui.theme.tabular
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/*
 * Chart conventions used across Forge:
 *  - Data marks use the brand primary colour; text always uses text colours.
 *  - Lines are 2dp, grids are hairlines in the outline colour, markers ≥ 8dp and only
 *    on the latest point and the one you touch.
 *  - Every chart can be tapped/dragged to read exact values, and each screen also
 *    lists the numbers underneath (no colour-only information).
 */

/** A headline number with an optional comparison, e.g. "4,320 kg  ↑ 12% vs last week". */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, delta: String? = null, deltaGood: Boolean? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall.tabular(), color = MaterialTheme.colorScheme.onSurface)
            delta?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (deltaGood) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.tertiary
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * One series over time. Drag or tap to read any point; the caption above shows it.
 * [points] are (epoch ms, value), oldest first.
 */
@Composable
fun LineChart(
    points: List<Pair<Long, Double>>,
    formatValue: (Double) -> String,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    description: String = "Line chart",
) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val shown = selected ?: points.lastIndex
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    val minV = points.minOf { it.second }
    val maxV = points.maxOf { it.second }
    val pad = ((maxV - minV) * 0.15).takeIf { it > 0 } ?: (abs(maxV) * 0.1 + 1)
    val lo = minV - pad
    val hi = maxV + pad
    val t0 = points.first().first
    val t1 = points.last().first.takeIf { it > t0 } ?: (t0 + 1)

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatValue(points[shown].second),
                style = MaterialTheme.typography.titleLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                formatTime(points[shown].first) + if (selected == null) " · latest" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = "$description. ${points.size} points, from ${formatValue(points.first().second)} " +
                        "to ${formatValue(points.last().second)}."
                }
                .pointerInput(points) {
                    fun pick(x: Float) {
                        val left = LEFT_AXIS.toPx()
                        val w = size.width - left
                        val t = t0 + ((x - left) / w).coerceIn(0f, 1f) * (t1 - t0)
                        selected = points.indices.minBy { abs(points[it].first - t) }
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(points) {
                    detectDragGestures(onDragEnd = {}) { change, _ ->
                        val left = LEFT_AXIS.toPx()
                        val w = size.width - left
                        val t = t0 + ((change.position.x - left) / w).coerceIn(0f, 1f) * (t1 - t0)
                        selected = points.indices.minBy { abs(points[it].first - t) }
                    }
                },
        ) {
            val left = LEFT_AXIS.toPx()
            val bottom = BOTTOM_AXIS.toPx()
            val w = size.width - left
            val h = size.height - bottom
            fun x(t: Long) = left + (t - t0).toFloat() / (t1 - t0) * w
            fun y(v: Double) = ((hi - v) / (hi - lo)).toFloat() * h

            // Recessive grid with 3 value labels.
            for (i in 0..2) {
                val v = lo + (hi - lo) * (0.15 + 0.35 * i)
                val gy = y(v)
                drawLine(grid, Offset(left, gy), Offset(size.width, gy), strokeWidth = 1f)
                val label = measurer.measure(formatValue(v), labelStyle)
                drawText(label, topLeft = Offset(0f, gy - label.size.height / 2f))
            }
            // Time labels: first and last.
            val first = measurer.measure(formatTime(t0), labelStyle)
            drawText(first, topLeft = Offset(left, h + 4.dp.toPx()))
            val last = measurer.measure(formatTime(points.last().first), labelStyle)
            drawText(last, topLeft = Offset(size.width - last.size.width, h + 4.dp.toPx()))

            // The line.
            val path = Path()
            points.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v)) }
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Crosshair + marker for the shown point (ring of surface so it reads on the line).
            val (st, sv) = points[shown]
            if (selected != null) drawLine(grid, Offset(x(st), 0f), Offset(x(st), h), strokeWidth = 1.dp.toPx())
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(st), y(sv)))
            drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(st), y(sv)))
        }
    }
}

private val LEFT_AXIS = 44.dp
private val BOTTOM_AXIS = 20.dp

/**
 * Training calendar: one column per week (Monday on top), darker = more volume.
 * Tap a day to see it in the caption.
 */
@Composable
fun TrainingCalendar(
    cells: List<HeatCell>,
    describe: (HeatCell) -> String,
    modifier: Modifier = Modifier,
) {
    if (cells.isEmpty()) return
    var selected by remember(cells) { mutableStateOf<HeatCell?>(null) }
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val full = MaterialTheme.colorScheme.primary
    val ramp = listOf(empty) + listOf(0.35f, 0.6f, 0.8f, 1f).map { lerp(empty, full, it) }
    val outline = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val weeks = cells.chunked(7)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics {
                    contentDescription = "Training calendar, ${cells.count { it.workouts > 0 }} training days in ${weeks.size} weeks"
                }
                .pointerInput(cells) {
                    detectTapGestures { p ->
                        val dayLabels = DAY_LABELS.toPx()
                        val top = MONTH_LABELS.toPx()
                        val cell = ((size.width - dayLabels) / weeks.size).coerceAtMost((size.height - top) / 7f)
                        val col = ((p.x - dayLabels) / cell).toInt()
                        val row = ((p.y - top) / cell).toInt()
                        selected = weeks.getOrNull(col)?.getOrNull(row)
                    }
                },
        ) {
            val dayLabels = DAY_LABELS.toPx()
            val top = MONTH_LABELS.toPx()
            val cell = ((size.width - dayLabels) / weeks.size).coerceAtMost((size.height - top) / 7f)
            val gap = 2.dp.toPx()
            listOf(0 to "M", 2 to "W", 4 to "F").forEach { (row, text) ->
                val m = measurer.measure(text, labelStyle)
                drawText(m, topLeft = Offset(0f, top + row * cell + (cell - m.size.height) / 2))
            }
            var lastMonth = -1
            weeks.forEachIndexed { col, days ->
                val month = days.first().date.monthValue
                if (month != lastMonth) {
                    val m = measurer.measure(days.first().date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), labelStyle)
                    drawText(m, topLeft = Offset(dayLabels + col * cell, 0f))
                    lastMonth = month
                }
                days.forEachIndexed { row, day ->
                    val topLeft = Offset(dayLabels + col * cell + gap / 2, top + row * cell + gap / 2)
                    val s = Size(cell - gap, cell - gap)
                    drawRoundRect(ramp[day.level], topLeft, s, CornerRadius(3.dp.toPx()))
                    if (day == selected) {
                        drawRoundRect(outline, topLeft, s, CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(
                selected?.let(describe) ?: "Tap a day for details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Less ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ramp.forEach { c ->
                Box(Modifier.padding(1.dp).size(10.dp).background(c, RoundedCornerShape(2.dp)))
            }
            Text(" More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val DAY_LABELS = 16.dp
private val MONTH_LABELS = 16.dp

/** One horizontal bar per row against a target tick, with the numbers written out. */
data class BarRow(val label: String, val value: Double, val target: Double, val valueText: String)

@Composable
fun TargetBars(rows: List<BarRow>, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.onSurface
    val scale = (rows.maxOfOrNull { maxOf(it.value, it.target) } ?: 1.0).coerceAtLeast(1.0) * 1.1
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(row.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
                Canvas(Modifier.weight(1f).height(14.dp)) {
                    val r = CornerRadius(4.dp.toPx())
                    drawRoundRect(track, size = size, cornerRadius = r)
                    val w = (row.value / scale).toFloat() * size.width
                    if (w > 0) drawRoundRect(fill, size = Size(w.coerceAtLeast(4.dp.toPx()), size.height), cornerRadius = r)
                    val tx = (row.target / scale).toFloat() * size.width
                    drawLine(tick, Offset(tx, -2f), Offset(tx, size.height + 2f), strokeWidth = 2.dp.toPx())
                }
                Text(
                    row.valueText,
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp).padding(start = Spacing.sm),
                )
            }
        }
    }
}
