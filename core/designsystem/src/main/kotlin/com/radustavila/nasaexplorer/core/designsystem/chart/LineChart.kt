package com.radustavila.nasaexplorer.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

const val LINE_CHART_TEST_TAG = "line-chart"

/** One value of a chart: [x] grows to the right (e.g. an epoch day), [y] upwards. */
@Immutable
data class ChartPoint(val x: Double, val y: Double)

/** The points a chart draws, wrapped so Compose can skip charts whose data did not change. */
@Immutable
data class ChartData(val points: List<ChartPoint>) {
    val minX: Double get() = points.minOf { it.x }
    val maxX: Double get() = points.maxOf { it.x }
    val minY: Double get() = points.minOf { it.y }
    val maxY: Double get() = points.maxOf { it.y }

    /** The index of the point nearest [x]; points are in [x] order. */
    fun nearestIndex(x: Double): Int {
        val index = points.binarySearch { it.x.compareTo(x) }
        if (index >= 0) return index
        val after = (-index - 1).coerceIn(0, points.lastIndex)
        val before = (after - 1).coerceAtLeast(0)
        return if (abs(points[before].x - x) <= abs(points[after].x - x)) before else after
    }
}

/**
 * A small line without axes, for cards: the trend at a glance, the latest value marked with a dot.
 * Draws nothing for fewer than two points.
 */
@Composable
fun Sparkline(
    data: ChartData,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier) {
        if (data.points.size < 2) return@Canvas
        val inset = 4.dp.toPx()
        val frame = ChartFrame(
            minX = data.minX,
            maxX = data.maxX,
            minY = data.minY,
            maxY = data.maxY,
            left = 0f,
            top = inset,
            right = size.width - inset,
            bottom = size.height - inset,
        )
        drawSeries(data, frame, color, strokeWidth.toPx())
        drawCircle(color, radius = strokeWidth.toPx() * 1.5f, center = frame.offset(data.points.last()))
    }
}

/**
 * A full line chart with value and time labels and a readout: the readout line above the chart
 * names the touched point (the latest until the user touches or drags along the chart).
 *
 * @param xTicks where to label the horizontal axis (e.g. every 20 years), in [ChartPoint.x] units.
 * @param formatX words an [xTicks] label.
 * @param formatY words a value label; vertical ticks are picked as round numbers of [ChartPoint.y].
 * @param readout words the selected point.
 * @param description what the chart shows, for screen readers.
 */
@Composable
fun LineChart(
    data: ChartData,
    xTicks: List<Double>,
    formatX: (Double) -> String,
    formatY: (Double) -> String,
    readout: (ChartPoint) -> String,
    description: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 220.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (data.points.isEmpty()) return
    var selected by rememberSaveable(data) { mutableStateOf(data.points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val yTicks = remember(data) { niceTicks(data.minY, data.maxY) }
    val yLabels = remember(yTicks, formatY) { yTicks.map(formatY) }
    val xLabels = remember(xTicks, formatX) { xTicks.map(formatX) }
    val labelWidth = remember(yLabels, labelStyle) { yLabels.maxOf { measurer.measure(it, labelStyle).size.width } }
    val labelHeight = remember(labelStyle) { measurer.measure("0", labelStyle).size.height }
    // Laid out the same way for drawing and for touches, so a touch picks the point under the finger.
    val frameOf: Density.(width: Float, height: Float) -> ChartFrame = remember(data, yTicks, labelWidth, labelHeight) {
        { width, height ->
            val gap = LABEL_GAP.toPx()
            ChartFrame(
                minX = data.minX,
                maxX = data.maxX,
                minY = minOf(yTicks.first(), data.minY),
                maxY = maxOf(yTicks.last(), data.maxY),
                left = labelWidth + gap,
                top = labelHeight / 2f,
                right = width - gap,
                bottom = height - labelHeight - gap,
            )
        }
    }

    Column(modifier) {
        Text(
            text = readout(data.points[selected.coerceIn(data.points.indices)]),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .testTag(LINE_CHART_TEST_TAG)
                .semantics { contentDescription = description }
                .pointerInput(data, frameOf) {
                    detectTapGestures { offset ->
                        selected = data.nearestIndex(frameOf(size.width.toFloat(), size.height.toFloat()).xAt(offset.x))
                    }
                }
                .pointerInput(data, frameOf) {
                    detectHorizontalDragGestures { change, _ ->
                        val frame = frameOf(size.width.toFloat(), size.height.toFloat())
                        selected = data.nearestIndex(frame.xAt(change.position.x))
                    }
                },
        ) {
            val chart = frameOf(size.width, size.height)
            drawYAxis(chart, yTicks, yLabels, measurer, labelStyle, gridColor)
            drawXAxis(chart, xTicks, xLabels, measurer, labelStyle, gridColor, LABEL_GAP.toPx())
            drawSeries(data, chart, color, 2.dp.toPx())
            val point = chart.offset(data.points[selected.coerceIn(data.points.indices)])
            drawLine(color.copy(alpha = 0.5f), Offset(point.x, chart.top), Offset(point.x, chart.bottom), 1.dp.toPx())
            drawCircle(color, radius = 4.dp.toPx(), center = point)
        }
    }
}

private val LABEL_GAP = 6.dp

/** Maps data to pixels inside a rectangle. */
private class ChartFrame(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    private val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
    private val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0

    fun x(value: Double): Float = left + ((value - minX) / spanX * (right - left)).toFloat()
    fun y(value: Double): Float = bottom - ((value - minY) / spanY * (bottom - top)).toFloat()
    fun offset(point: ChartPoint) = Offset(x(point.x), y(point.y))
    fun xAt(pixel: Float): Double = minX + (pixel - left) / (right - left) * spanX
}

private fun DrawScope.drawSeries(data: ChartData, frame: ChartFrame, color: Color, width: Float) {
    val path = Path()
    data.points.forEachIndexed { index, point ->
        val x = frame.x(point.x)
        val y = frame.y(point.y)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawYAxis(
    frame: ChartFrame,
    ticks: List<Double>,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
) {
    ticks.forEachIndexed { index, tick ->
        val y = frame.y(tick)
        drawLine(gridColor, Offset(frame.left, y), Offset(frame.right, y), 1.dp.toPx())
        val label = measurer.measure(labels[index], style)
        drawText(label, topLeft = Offset(frame.left - 6.dp.toPx() - label.size.width, y - label.size.height / 2f))
    }
}

private fun DrawScope.drawXAxis(
    frame: ChartFrame,
    ticks: List<Double>,
    labels: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    gridColor: Color,
    gap: Float,
) {
    ticks.forEachIndexed { index, tick ->
        if (tick < frame.minX || tick > frame.maxX) return@forEachIndexed
        val x = frame.x(tick)
        drawLine(gridColor, Offset(x, frame.bottom), Offset(x, frame.bottom + gap / 2), 1.dp.toPx())
        val label = measurer.measure(labels[index], style)
        val left = (x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(left, frame.bottom + gap / 2))
    }
}

/**
 * About [count] round values (1, 2 or 5 times a power of ten apart) covering [min]..[max], for
 * axis labels: 300, 350, 400, 450 rather than 318.4, 345.2, ….
 */
fun niceTicks(min: Double, max: Double, count: Int = 5): List<Double> {
    if (max <= min) return listOf(min)
    val rough = (max - min) / (count - 1).coerceAtLeast(1)
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val first = floor(min / step) * step
    val last = ceil(max / step) * step
    val steps = Math.round((last - first) / step).toInt()
    return (0..steps).map { i -> first + i * step }
}
