package com.radustavila.nasaexplorer.feature.comets.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.CometEphemerisPoint
import com.radustavila.nasaexplorer.feature.comets.R
import java.time.Instant

const val BRIGHTNESS_CURVE_TEST_TAG = "comet-brightness-curve"

/**
 * The predicted magnitude over the cached days, brighter higher up (magnitudes run backwards), with
 * today as a dashed line. Draws nothing for fewer than two predicted days.
 */
@Composable
internal fun BrightnessCurve(points: List<CometEphemerisPoint>, now: Instant, modifier: Modifier = Modifier) {
    val curve = points.mapNotNull { point -> point.magnitude?.let { point.time to it } }
    if (curve.size < 2) return
    val units = rememberUnitFormatter()
    val brightest = curve.minOf { it.second }
    val faintest = curve.maxOf { it.second }
    val first = curve.first().first
    val last = curve.last().first
    val lineColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.comets_details_curve_description, units.day(first), units.day(last))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            units.magnitude(brightest),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CURVE_HEIGHT)
                .testTag(BRIGHTNESS_CURVE_TEST_TAG)
                .semantics { contentDescription = description },
        ) {
            drawCurve(curve, now, CurveColors(line = lineColor, today = todayColor, grid = gridColor))
        }
        Text(
            units.magnitude(brightest + maxOf(faintest - brightest, MIN_MAGNITUDE_RANGE)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(units.day(first), style = MaterialTheme.typography.labelSmall)
            Text(
                stringResource(R.string.comets_details_curve_today),
                style = MaterialTheme.typography.labelSmall,
                color = todayColor,
            )
            Text(units.day(last), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private class CurveColors(val line: Color, val today: Color, val grid: Color)

/** The curve over the whole canvas, brightest at the top, with today as a dashed line. */
private fun DrawScope.drawCurve(curve: List<Pair<Instant, Double>>, now: Instant, colors: CurveColors) {
    val first = curve.first().first
    val last = curve.last().first
    val brightest = curve.minOf { it.second }
    val span = (last.toEpochMilli() - first.toEpochMilli()).toFloat()
    // A flat curve still needs a range to draw in.
    val range = (curve.maxOf { it.second } - brightest).coerceAtLeast(MIN_MAGNITUDE_RANGE).toFloat()
    fun x(time: Instant) = (time.toEpochMilli() - first.toEpochMilli()) / span * size.width
    fun y(magnitude: Double) = (magnitude - brightest).toFloat() / range * size.height

    drawLine(colors.grid, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    drawLine(colors.grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
    if (!now.isBefore(first) && !now.isAfter(last)) {
        drawLine(
            color = colors.today,
            start = Offset(x(now), 0f),
            end = Offset(x(now), size.height),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH, DASH)),
        )
    }
    val path = Path().apply {
        curve.forEachIndexed { index, (time, magnitude) ->
            if (index == 0) moveTo(x(time), y(magnitude)) else lineTo(x(time), y(magnitude))
        }
    }
    drawPath(path, colors.line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
}

private val CURVE_HEIGHT = 140.dp
private const val MIN_MAGNITUDE_RANGE = 1.0
private const val DASH = 12f
