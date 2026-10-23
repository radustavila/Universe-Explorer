package com.radustavila.nasaexplorer.feature.webb.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.WebbVisit
import com.radustavila.nasaexplorer.feature.webb.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The visit's headline: its target, or "Calibration" for calibration and engineering visits. */
@Composable
internal fun WebbVisit.title(): String =
    if (isCalibration) stringResource(R.string.webb_calibration) else targetName.orEmpty()

/** The instrument and mode as STScI writes it, or what an engineering visit without one does. */
@Composable
internal fun WebbVisit.instrumentLine(): String = instrument.ifBlank { stringResource(engineeringRes()) }

@StringRes
private fun WebbVisit.engineeringRes(): Int = when {
    visitType.contains("MOMENTUM", ignoreCase = true) -> R.string.webb_engineering_momentum
    visitType.contains("STATION KEEPING", ignoreCase = true) -> R.string.webb_engineering_station_keeping
    visitType.contains("WFSC", ignoreCase = true) -> R.string.webb_engineering_wavefront
    else -> R.string.webb_engineering_other
}

/**
 * "Star · Exoplanet Systems": the category in the app's words where it is one STScI uses (raw
 * otherwise), then STScI's keywords. A calibration visit's headline already says "Calibration", so
 * its category is left out. Null when nothing is left.
 */
@Composable
internal fun WebbVisit.subjectLine(): String? {
    val category = category
        ?.takeUnless { isCalibration && categoryRes(it) == R.string.webb_category_calibration }
        ?.let { raw -> categoryRes(raw)?.let { stringResource(it) } ?: raw }
    return listOfNotNull(category, keywords).takeIf { it.isNotEmpty() }
        ?.joinToString(stringResource(R.string.webb_separator))
}

@StringRes
internal fun categoryRes(category: String): Int? = when (category.trim().lowercase()) {
    "calibration" -> R.string.webb_category_calibration
    "clusters of galaxies" -> R.string.webb_category_clusters_of_galaxies
    "galaxy" -> R.string.webb_category_galaxy
    "ism" -> R.string.webb_category_ism
    "solar system" -> R.string.webb_category_solar_system
    "star" -> R.string.webb_category_star
    "stellar cluster" -> R.string.webb_category_stellar_cluster
    "exoplanet" -> R.string.webb_category_exoplanet
    "unidentified" -> R.string.webb_category_unidentified
    else -> null
}

/** "45 min", "8 h 33 min" or "1 d 2 h", rounded down to the minute. */
@Composable
internal fun durationText(duration: Duration): String {
    val minutes = duration.toMinutes().toInt()
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        days > 0 -> stringResource(R.string.webb_duration_days, days, hours % HOURS_PER_DAY)
        hours > 0 -> stringResource(R.string.webb_duration_hours, hours, minutes % MINUTES_PER_HOUR)
        else -> stringResource(R.string.webb_duration_minutes, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24

/** A clock time for [instant], with its day when it is not on [now]'s day in the device's zone. */
internal fun UnitFormatter.timeNear(instant: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    if (instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) time(instant) else dayAndTime(instant)
