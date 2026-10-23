package com.radustavila.nasaexplorer.feature.milkyway.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.TimeInterval
import com.radustavila.nasaexplorer.feature.milkyway.R
import java.time.Instant

/**
 * Everything about one night: the window and where the core is, the core's rise, highest point and
 * set, the Moon, then the evening's and the morning's light in time order.
 */
@Composable
internal fun NightDetails(night: MilkyWayNight, units: UnitFormatter, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val window = night.window
        if (window != null) {
            Text(
                text = intervalText(window.start, window.end, units),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(corePositionText(window, units), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = night.noWindowReasonRes().let { reason ->
                    if (reason == R.string.milky_way_reason_moon) {
                        stringResource(reason, night.moonIlluminatedFraction.percent())
                    } else {
                        stringResource(reason)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        coreLine(night, units)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text(moonLine(night, units), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LightColumn(
                title = stringResource(R.string.milky_way_evening),
                units = units,
                rows = listOf(
                    Light(R.string.milky_way_golden_hour, interval = night.evening.goldenHour),
                    Light(R.string.milky_way_sunset, at = night.twilight.sunset),
                    Light(R.string.milky_way_blue_hour, interval = night.evening.blueHour),
                    Light(R.string.milky_way_civil_dusk, at = night.twilight.civilDusk),
                    Light(R.string.milky_way_nautical_dusk, at = night.twilight.nauticalDusk),
                    Light(R.string.milky_way_astronomical_dusk, at = night.twilight.astronomicalDusk),
                ),
                modifier = Modifier.weight(1f),
            )
            LightColumn(
                title = stringResource(R.string.milky_way_morning),
                units = units,
                rows = listOf(
                    Light(R.string.milky_way_astronomical_dawn, at = night.twilight.astronomicalDawn),
                    Light(R.string.milky_way_nautical_dawn, at = night.twilight.nauticalDawn),
                    Light(R.string.milky_way_civil_dawn, at = night.twilight.civilDawn),
                    Light(R.string.milky_way_blue_hour, interval = night.morning.blueHour),
                    Light(R.string.milky_way_sunrise, at = night.twilight.sunrise),
                    Light(R.string.milky_way_golden_hour, interval = night.morning.goldenHour),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Core: rises 21:30 · highest 23:50 · sets 04:10", leaving out what happens outside the night. */
@Composable
private fun coreLine(night: MilkyWayNight, units: UnitFormatter): String? {
    val parts = listOfNotNull(
        night.coreRise?.let { stringResource(R.string.milky_way_core_rises, units.time(it)) },
        night.coreTransit?.let { stringResource(R.string.milky_way_core_highest, units.time(it)) },
        night.coreSet?.let { stringResource(R.string.milky_way_core_sets, units.time(it)) },
    )
    val separator = stringResource(R.string.milky_way_core_separator)
    return parts.takeIf { it.isNotEmpty() }
        ?.let { stringResource(R.string.milky_way_core_line, it.joinToString(separator)) }
}

@Composable
private fun moonLine(night: MilkyWayNight, units: UnitFormatter): String {
    val lit = night.moonIlluminatedFraction.percent()
    val up = night.moonUp
    return when {
        up.isEmpty() -> stringResource(R.string.milky_way_moon_down, lit)
        up.singleOrNull()?.let { it.start == night.start && it.end == night.end } == true ->
            stringResource(R.string.milky_way_moon_up_all_night, lit)
        else -> stringResource(
            R.string.milky_way_moon_up,
            lit,
            up.map { intervalText(it, units) }.joinToString(stringResource(R.string.milky_way_month_list_separator)),
        )
    }
}

/** One line of the light table: a moment ([at]) or a stretch ([interval]); left out when the Sun skips it. */
private data class Light(@StringRes val label: Int, val at: Instant? = null, val interval: TimeInterval? = null)

@Composable
private fun LightColumn(title: String, units: UnitFormatter, rows: List<Light>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        rows.forEach { light ->
            val value = light.interval?.let { intervalText(it, units) } ?: light.at?.let(units::time)
            if (value != null) {
                Row {
                    Text(
                        stringResource(light.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(value, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
