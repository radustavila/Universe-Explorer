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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.feature.spaceweather.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

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
            .clearAndSetSemantics { contentDescription = summary },
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
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { (day, slots) ->
                Text(
                    text = dayNames.getValue(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(slots.size.toFloat()),
                )
            }
        }
    }
}

@Composable
private fun dayName(day: LocalDate, today: LocalDate?): String =
    if (day == today) {
        stringResource(R.string.space_weather_today)
    } else {
        day.dayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale())
    }

private val BAR_AREA_HEIGHT = 96.dp

/** Kp 0 still gets a sliver, so a quiet slot reads as "quiet", not "missing". */
private const val MIN_BAR_FRACTION = 0.04f
