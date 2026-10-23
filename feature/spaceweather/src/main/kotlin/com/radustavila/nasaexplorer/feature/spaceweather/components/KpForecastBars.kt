package com.radustavila.nasaexplorer.feature.spaceweather.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToInt

const val KP_FORECAST_TEST_TAG = "kp-forecast"

/**
 * One bar per 3-hour slot, grouped by local day, coloured like the gauge. A dashed line marks
 * [threshold], the Kp the aurora needs to reach the user's place, when alerts know it.
 */
@Composable
internal fun KpForecastBars(
    forecast: List<KpReading>,
    threshold: Int?,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val days = forecast.groupBy { it.start.atZone(zone).toLocalDate() }
    val today = forecast.firstOrNull()?.start?.atZone(zone)?.toLocalDate()
    val dayNames = days.keys.associateWith { day -> dayName(day, today) }
    val daySummaries = mutableListOf<String>()
    for ((day, slots) in days) {
        daySummaries += stringResource(
            R.string.space_weather_forecast_day_summary,
            dayNames.getValue(day),
            Kp.level(slots.maxOf { it.kp }),
        )
    }
    val summary = daySummaries.joinToString(". ")
    val lineColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .testTag(KP_FORECAST_TEST_TAG)
            .semantics { contentDescription = summary },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT)
                .drawBehind {
                    if (threshold != null) {
                        val y = size.height * (1f - threshold.toFloat() / Kp.MAX)
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(BAR_SPACING),
            verticalAlignment = Alignment.Bottom,
        ) {
            forecast.forEach { reading ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(((reading.kp / Kp.MAX).toFloat()).coerceIn(MIN_BAR_FRACTION, 1f))
                        .background(kpColor(reading.level), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        }
        DayLabels(
            labels = days.keys.map { dayNames.getValue(it) },
            firstSlots = days.values.runningFold(0) { first, slots -> first + slots.size }.dropLast(1),
            slotCount = forecast.size,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/**
 * Each day's name starts under the day's first bar. A label that would run into the next one (or
 * past the end) is left out: a partial "today" of one or two slots has no room for its name, and
 * the next day's label then marks where tomorrow begins.
 */
@Composable
private fun DayLabels(
    labels: List<String>,
    firstSlots: List<Int>,
    slotCount: Int,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    // The forecast's content description already reads the days out.
                    modifier = Modifier.semantics { hideFromAccessibility() },
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        // Bars are spaced BAR_SPACING apart, so slot k starts at k * (width + spacing) / slotCount.
        val slotPitch = (width + BAR_SPACING.toPx()) / slotCount
        val positions = dayLabelPositions(
            starts = firstSlots.map { (it * slotPitch).roundToInt() },
            widths = placeables.map { it.width },
            rowWidth = width,
            minGap = LABEL_GAP.roundToPx(),
        )
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { index, placeable ->
                positions[index]?.let { x -> placeable.placeRelative(x, 0) }
            }
        }
    }
}

/**
 * Where each label goes, or null when it doesn't fit between its own [starts] position and the
 * next label's (keeping [minGap] free), or the end of the row for the last one.
 */
internal fun dayLabelPositions(starts: List<Int>, widths: List<Int>, rowWidth: Int, minGap: Int): List<Int?> =
    starts.indices.map { index ->
        val limit = starts.getOrNull(index + 1)?.minus(minGap) ?: rowWidth
        starts[index].takeIf { it + widths[index] <= limit }
    }

@Composable
private fun dayName(day: LocalDate, today: LocalDate?): String =
    if (day == today) {
        stringResource(R.string.space_weather_today)
    } else {
        day.dayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale())
    }

private val BAR_AREA_HEIGHT = 96.dp
private val BAR_SPACING = 2.dp
private val LABEL_GAP = 4.dp

/** Kp 0 still gets a sliver, so a quiet slot reads as "quiet", not "missing". */
private const val MIN_BAR_FRACTION = 0.04f
