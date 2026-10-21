package com.radustavila.nasaexplorer.feature.sky.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.formatClockTime
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.feature.sky.R
import java.text.NumberFormat
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/**
 * Times are shown in the device's time zone (the zone a user watching from a picked city far away
 * gets is named on the screen), in the user's 12/24-hour choice.
 */
@Composable
internal fun Instant.formatTime(): String = formatClockTime()

@Composable
internal fun Instant.formatDay(): String = rememberUnitFormatter().day(this)

@Composable
internal fun Instant.formatDayAndTime(): String = rememberUnitFormatter().dayAndTime(this)

internal fun Double.formatDecimal(fractionDigits: Int, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(this)

@Composable
internal fun GeoCoordinate.label(): String {
    val latitudeText = abs(latitude).formatDecimal(2)
    val longitudeText = abs(longitude).formatDecimal(2)
    return stringResource(
        R.string.sky_location_coordinates,
        stringResource(if (latitude >= 0) R.string.sky_latitude_north else R.string.sky_latitude_south, latitudeText),
        stringResource(if (longitude >= 0) R.string.sky_longitude_east else R.string.sky_longitude_west, longitudeText),
    )
}

@StringRes
internal fun MoonPhase.labelRes(): Int = when (this) {
    MoonPhase.NEW_MOON -> R.string.moon_phase_new
    MoonPhase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
    MoonPhase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
    MoonPhase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
    MoonPhase.FULL_MOON -> R.string.moon_phase_full
    MoonPhase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
    MoonPhase.LAST_QUARTER -> R.string.moon_phase_last_quarter
    MoonPhase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
}

@StringRes
internal fun Planet.labelRes(): Int = when (this) {
    Planet.MERCURY -> R.string.planet_mercury
    Planet.VENUS -> R.string.planet_venus
    Planet.MARS -> R.string.planet_mars
    Planet.JUPITER -> R.string.planet_jupiter
    Planet.SATURN -> R.string.planet_saturn
}

private val COMPASS = intArrayOf(
    R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
    R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw,
)

private val COMPASS_SHORT = intArrayOf(
    R.string.compass_short_n, R.string.compass_short_ne, R.string.compass_short_e, R.string.compass_short_se,
    R.string.compass_short_s, R.string.compass_short_sw, R.string.compass_short_w, R.string.compass_short_nw,
)

private const val COMPASS_SECTOR = 45.0
private const val FULL_TURN = 360.0

private fun compassIndex(azimuth: Double): Int =
    ((((azimuth % FULL_TURN) + FULL_TURN) % FULL_TURN + COMPASS_SECTOR / 2) / COMPASS_SECTOR).toInt() % COMPASS.size

@StringRes
internal fun compassPointRes(azimuth: Double): Int = COMPASS[compassIndex(azimuth)]

@StringRes
internal fun compassShortRes(azimuth: Double): Int = COMPASS_SHORT[compassIndex(azimuth)]
