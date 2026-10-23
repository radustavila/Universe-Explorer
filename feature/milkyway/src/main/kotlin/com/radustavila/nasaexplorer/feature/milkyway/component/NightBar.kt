package com.radustavila.nasaexplorer.feature.milkyway.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.feature.milkyway.R
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** The colours of the night bars and their legend. */
internal object NightBarColors {
    val twilight = Color(0xFF3B4C86)
    val dark = Color(0xFF111735)
    val moon = Color(0xFFECEFF1)
    val core = Color(0xFFFF9E80)
}

/**
 * The clock-time span every night's bar shares, in minutes after the last [pivotHour] o'clock (in
 * [zone]) before each night starts, so a night's bar lines up under the others whatever its date.
 * The pivot sits a few hours before the first sunset: for a place far from the device's time zone
 * the night can start at any clock time, even around noon.
 */
@Immutable
internal data class NightAxis(
    val fromMinutes: Long,
    val untilMinutes: Long,
    val zone: ZoneId,
    val pivotHour: Int = NOON_HOUR,
) {

    /** Where [at] falls on [night]'s bar, 0 at the left edge to 1 at the right. */
    fun fraction(night: MilkyWayNight, at: Instant): Float {
        val minutes = Duration.between(pivotBefore(night), at).toMinutes()
        return ((minutes - fromMinutes).toFloat() / (untilMinutes - fromMinutes)).coerceIn(0f, 1f)
    }

    /**
     * Every third o'clock on the axis (every sixth on a long one), as (fraction, an instant at that
     * clock time).
     */
    fun ticks(reference: MilkyWayNight): List<Pair<Float, Instant>> {
        val stepHours = if (untilMinutes - fromMinutes > LONG_AXIS_MINUTES) LONG_TICK_HOURS else TICK_HOURS
        val pivot = pivotBefore(reference)
        val firstHour = (fromMinutes + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR
        return (firstHour..untilMinutes / MINUTES_PER_HOUR)
            .filter { hour -> (hour + pivotHour) % stepHours == 0L }
            .map { hour ->
                val at = pivot.plus(Duration.ofHours(hour))
                (hour * MINUTES_PER_HOUR - fromMinutes).toFloat() / (untilMinutes - fromMinutes) to at
            }
    }

    private fun pivotBefore(night: MilkyWayNight): Instant {
        val start = night.start.atZone(zone)
        val sameDay = start.toLocalDate().atTime(LocalTime.of(pivotHour, 0)).atZone(zone)
        return (if (sameDay.isAfter(start)) sameDay.minusDays(1) else sameDay).toInstant()
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60L
        private const val NOON_HOUR = 12
        private const val HOURS_PER_DAY = 24
        private const val PIVOT_BEFORE_SUNSET_HOURS = 6
        private const val TICK_HOURS = 3L
        private const val LONG_TICK_HOURS = 6L
        private const val LONG_AXIS_MINUTES = 15 * 60L
        private const val DAY_MINUTES = 24 * 60L

        /** From the earliest sunset to the latest sunrise, rounded out to whole hours. */
        fun of(nights: List<MilkyWayNight>, zone: ZoneId): NightAxis {
            val dark = nights.filter { it.darkness != NightDarkness.NONE || it.twilight.sunset != null }
            val first = dark.firstOrNull() ?: return NightAxis(0, DAY_MINUTES, zone)
            val pivotHour = (first.start.atZone(zone).hour - PIVOT_BEFORE_SUNSET_HOURS).mod(HOURS_PER_DAY)
            val probe = NightAxis(0, DAY_MINUTES, zone, pivotHour)
            val from = dark.minOf { Duration.between(probe.pivotBefore(it), it.start).toMinutes() }
            val until = dark.maxOf { Duration.between(probe.pivotBefore(it), it.end).toMinutes() }
            val start = (from.coerceIn(0, DAY_MINUTES) / MINUTES_PER_HOUR) * MINUTES_PER_HOUR
            val end = ((until.coerceIn(0, DAY_MINUTES) + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR) * MINUTES_PER_HOUR
            return NightAxis(start, end.coerceAtLeast(start + MINUTES_PER_HOUR), zone, pivotHour)
        }
    }
}

/**
 * One night as a bar on [axis]: day, twilight and full darkness as the background, the Moon above
 * the horizon along the top, the core above 10° along the bottom and the best window across the
 * middle.
 */
@Composable
internal fun NightBar(night: MilkyWayNight, axis: NightAxis, modifier: Modifier = Modifier) {
    val day = MaterialTheme.colorScheme.surfaceContainerHighest
    val window = MaterialTheme.colorScheme.primary
    Canvas(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(BAR_CORNER)),
    ) {
        drawRect(day)
        // Sunset to sunrise; a day without either is all day, or all night when it stays dark.
        if (night.twilight.sunset != null || night.darkness != NightDarkness.NONE) {
            span(axis, night, night.start, night.end, NightBarColors.twilight, top = 0f, height = 1f)
        }
        val darkStart = night.twilight.astronomicalDusk ?: night.start.takeIf { night.isDarkThroughout() }
        val darkEnd = night.twilight.astronomicalDawn ?: night.end.takeIf { night.isDarkThroughout() }
        if (darkStart != null && darkEnd != null) {
            span(axis, night, darkStart, darkEnd, NightBarColors.dark, top = 0f, height = 1f)
        }
        val moonAlpha = MOON_MIN_ALPHA + (1 - MOON_MIN_ALPHA) * night.moonIlluminatedFraction.toFloat()
        night.moonUp.forEach {
            span(axis, night, it.start, it.end, NightBarColors.moon.copy(alpha = moonAlpha), top = 0f, height = LANE)
        }
        night.coreUp.forEach {
            span(axis, night, it.start, it.end, NightBarColors.core, top = 1f - LANE, height = LANE)
        }
        night.window?.let {
            span(axis, night, it.start, it.end, window, top = WINDOW_TOP, height = 1f - 2 * WINDOW_TOP, rounded = true)
        }
    }
}

private fun MilkyWayNight.isDarkThroughout(): Boolean =
    darkness == NightDarkness.ASTRONOMICAL && twilight.sunset == null

@Suppress("LongParameterList")
private fun DrawScope.span(
    axis: NightAxis,
    night: MilkyWayNight,
    from: Instant,
    until: Instant,
    color: Color,
    top: Float,
    height: Float,
    rounded: Boolean = false,
) {
    val left = axis.fraction(night, from) * size.width
    val right = axis.fraction(night, until) * size.width
    if (right <= left) return
    val topLeft = Offset(left, top * size.height)
    val area = Size(right - left, height * size.height)
    if (rounded) {
        val radius = WINDOW_CORNER.toPx()
        drawRoundRect(color, topLeft, area, CornerRadius(radius, radius))
    } else {
        drawRect(color, topLeft, area)
    }
}

/** Clock times under the bars, centred on their ticks. */
@Composable
internal fun AxisLabels(
    axis: NightAxis,
    reference: MilkyWayNight,
    units: UnitFormatter,
    modifier: Modifier = Modifier,
) {
    val ticks = axis.ticks(reference)
    Layout(
        content = {
            ticks.forEach { (_, at) ->
                Text(
                    units.time(at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val gap = LABEL_GAP.roundToPx()
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            // Left to right, skipping a label that would run into the one before.
            var freeFrom = 0
            placeables.forEachIndexed { index, placeable ->
                val center = ticks[index].first * width
                val x = (center - placeable.width / 2f).toInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                if (x >= freeFrom) {
                    placeable.place(x, 0)
                    freeFrom = x + placeable.width + gap
                }
            }
        }
    }
}

/** What the bars' colours mean. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NightBarLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(NightBarColors.dark, stringResource(R.string.milky_way_legend_dark))
        LegendItem(NightBarColors.twilight, stringResource(R.string.milky_way_legend_twilight))
        LegendItem(NightBarColors.moon, stringResource(R.string.milky_way_legend_moon))
        LegendItem(NightBarColors.core, stringResource(R.string.milky_way_legend_core))
        LegendItem(MaterialTheme.colorScheme.primary, stringResource(R.string.milky_way_legend_window))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val BAR_HEIGHT = 28.dp
private val BAR_CORNER = 6.dp
private val WINDOW_CORNER = 4.dp
private val LABEL_GAP = 8.dp

/** The Moon's and the core's lanes, as a share of the bar's height. */
private const val LANE = 0.26f
private const val WINDOW_TOP = 0.32f

/** A thin crescent still shows; a full Moon is drawn at full strength. */
private const val MOON_MIN_ALPHA = 0.35f
