package com.radustavila.nasaexplorer.feature.milkyway.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MilkyWayNight
import com.radustavila.nasaexplorer.core.model.MilkyWayWindow
import com.radustavila.nasaexplorer.core.model.NightDarkness
import com.radustavila.nasaexplorer.core.model.TimeInterval
import com.radustavila.nasaexplorer.feature.milkyway.R
import java.time.Duration
import java.time.Instant
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.roundToInt

private val COMPASS = intArrayOf(
    R.string.milky_way_compass_n,
    R.string.milky_way_compass_ne,
    R.string.milky_way_compass_e,
    R.string.milky_way_compass_se,
    R.string.milky_way_compass_s,
    R.string.milky_way_compass_sw,
    R.string.milky_way_compass_w,
    R.string.milky_way_compass_nw,
)

private const val COMPASS_SECTOR = 45.0
private const val FULL_TURN = 360.0
private const val PERCENT = 100.0
private const val MINUTES_PER_HOUR = 60L

/** "south-west" for an azimuth near 225 degrees. */
@StringRes
internal fun compassPointRes(azimuth: Double): Int =
    COMPASS[((azimuth.mod(FULL_TURN) + COMPASS_SECTOR / 2) / COMPASS_SECTOR).toInt() % COMPASS.size]

/** "23:40–02:10" in the user's clock format. */
@Composable
@ReadOnlyComposable
internal fun intervalText(start: Instant, end: Instant, units: UnitFormatter): String =
    stringResource(R.string.milky_way_interval, units.time(start), units.time(end))

@Composable
@ReadOnlyComposable
internal fun intervalText(interval: TimeInterval, units: UnitFormatter): String =
    intervalText(interval.start, interval.end, units)

/** "Core 18° high in the south-west at 00:30". */
@Composable
@ReadOnlyComposable
internal fun corePositionText(window: MilkyWayWindow, units: UnitFormatter): String = stringResource(
    R.string.milky_way_core_position,
    window.coreAtBest.altitudeDegrees.roundToInt(),
    stringResource(compassPointRes(window.coreAtBest.azimuthDegrees)),
    units.time(window.best),
)

/** "2 h 05 min" or "45 min". */
@Composable
@ReadOnlyComposable
internal fun durationText(duration: Duration): String {
    val minutes = duration.toMinutes()
    return if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.milky_way_minutes, minutes.toInt())
    } else {
        val hours = (minutes / MINUTES_PER_HOUR).toInt()
        stringResource(R.string.milky_way_hours_minutes, hours, (minutes % MINUTES_PER_HOUR).toInt())
    }
}

internal fun Double.percent(): Int = (this * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())

/** Why [night] has no window: too light, no core in the dark, or the Moon. */
@StringRes
internal fun MilkyWayNight.noWindowReasonRes(): Int = when {
    darkness != NightDarkness.ASTRONOMICAL -> R.string.milky_way_reason_not_dark
    !hasCoreInDark() -> R.string.milky_way_reason_core_down
    else -> R.string.milky_way_reason_moon
}

private fun MilkyWayNight.hasCoreInDark(): Boolean {
    val darkStart = twilight.astronomicalDusk ?: start
    val darkEnd = twilight.astronomicalDawn ?: end
    return coreUp.any { it.start < darkEnd && it.end > darkStart }
}

/**
 * "March to October", or "November to January, March to May" for a season in two parts; wraps
 * round the new year. Null for no month.
 */
@Composable
@ReadOnlyComposable
internal fun seasonText(months: Set<Month>): String? {
    val locale = Locale.current.platformLocale
    val resources = LocalResources.current
    val separator = stringResource(R.string.milky_way_month_list_separator)
    fun Month.label(): String = getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return when {
        months.isEmpty() -> null
        months.size == Month.entries.size -> stringResource(R.string.milky_way_season_all_year)
        else -> monthRuns(months).joinToString(separator) { (first, last) ->
            if (first == last) {
                first.label()
            } else {
                resources.getString(R.string.milky_way_month_range, first.label(), last.label())
            }
        }
    }
}

/** Runs of consecutive months, each as (first, last), starting after a month not in [months]. */
internal fun monthRuns(months: Set<Month>): List<Pair<Month, Month>> {
    val start = Month.entries.first { it !in months }
    val runs = mutableListOf<Pair<Month, Month>>()
    var runStart: Month? = null
    var previous = start
    for (step in 1..Month.entries.size) {
        val month = start.plus(step.toLong())
        if (month in months && runStart == null) runStart = month
        if (month !in months && runStart != null) {
            runs += runStart to previous
            runStart = null
        }
        previous = month
    }
    return runs
}
