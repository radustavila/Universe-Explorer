package com.radustavila.nasaexplorer.feature.exoplanets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import com.radustavila.nasaexplorer.core.model.DiscoveryMethod
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats quantities with as many decimals as their size deserves: 0.0892, 1.15, 13.5, 4,000.
 * Small ratios keep two significant digits so a tiny planet does not read as "0× Jupiter".
 */
internal fun formatQuantity(value: Double, locale: Locale = Locale.getDefault()): String {
    val magnitude = abs(value)
    val decimals = when {
        magnitude == 0.0 -> 0
        magnitude < SMALL -> 3
        magnitude < ONE -> 2
        magnitude < TEN -> 2
        magnitude < HUNDRED -> 1
        else -> 0
    }
    return NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = decimals
    }.format(value)
}

/** Temperatures: whole degrees, which is all an equilibrium estimate supports. */
internal fun formatWhole(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(value.roundToLong())

internal fun formatCount(value: Int, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getIntegerInstance(locale).format(value)

/** Light-years: one decimal for the neighbourhood, whole numbers beyond. */
internal fun formatLightYears(value: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = if (abs(value) < HUNDRED) 1 else 0
    }.format(value)

internal fun formatYearMonth(value: YearMonth, locale: Locale = Locale.getDefault()): String =
    value.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

@Composable
internal fun currentLocale(): Locale = LocalLocale.current.platformLocale

/** Proxima Centauri, the nearest star: 1.3012 parsecs. */
internal const val PROXIMA_CENTAURI_LIGHT_YEARS = 4.2465

@get:StringRes
internal val DiscoveryMethod.labelRes: Int
    get() = when (this) {
        DiscoveryMethod.TRANSIT -> R.string.discovery_method_transit
        DiscoveryMethod.RADIAL_VELOCITY -> R.string.discovery_method_radial_velocity
        DiscoveryMethod.MICROLENSING -> R.string.discovery_method_microlensing
        DiscoveryMethod.IMAGING -> R.string.discovery_method_imaging
        DiscoveryMethod.TRANSIT_TIMING -> R.string.discovery_method_transit_timing
        DiscoveryMethod.ECLIPSE_TIMING -> R.string.discovery_method_eclipse_timing
        DiscoveryMethod.ORBITAL_BRIGHTNESS -> R.string.discovery_method_orbital_brightness
        DiscoveryMethod.PULSAR_TIMING -> R.string.discovery_method_pulsar_timing
        DiscoveryMethod.PULSATION_TIMING -> R.string.discovery_method_pulsation_timing
        DiscoveryMethod.ASTROMETRY -> R.string.discovery_method_astrometry
        DiscoveryMethod.DISK_KINEMATICS -> R.string.discovery_method_disk_kinematics
        DiscoveryMethod.OTHER -> R.string.discovery_method_other
    }

/** One line on how the method finds a planet. */
@get:StringRes
internal val DiscoveryMethod.explanationRes: Int
    get() = when (this) {
        DiscoveryMethod.TRANSIT -> R.string.discovery_method_transit_explained
        DiscoveryMethod.RADIAL_VELOCITY -> R.string.discovery_method_radial_velocity_explained
        DiscoveryMethod.MICROLENSING -> R.string.discovery_method_microlensing_explained
        DiscoveryMethod.IMAGING -> R.string.discovery_method_imaging_explained
        DiscoveryMethod.TRANSIT_TIMING -> R.string.discovery_method_transit_timing_explained
        DiscoveryMethod.ECLIPSE_TIMING -> R.string.discovery_method_eclipse_timing_explained
        DiscoveryMethod.ORBITAL_BRIGHTNESS -> R.string.discovery_method_orbital_brightness_explained
        DiscoveryMethod.PULSAR_TIMING -> R.string.discovery_method_pulsar_timing_explained
        DiscoveryMethod.PULSATION_TIMING -> R.string.discovery_method_pulsation_timing_explained
        DiscoveryMethod.ASTROMETRY -> R.string.discovery_method_astrometry_explained
        DiscoveryMethod.DISK_KINEMATICS -> R.string.discovery_method_disk_kinematics_explained
        DiscoveryMethod.OTHER -> R.string.discovery_method_other_explained
    }

private const val SMALL = 0.1
private const val ONE = 1.0
private const val TEN = 10.0
private const val HUNDRED = 100.0

@get:StringRes
internal val ExoplanetList.labelRes: Int
    get() = when (this) {
        ExoplanetList.NEWEST -> R.string.exoplanets_list_newest
        ExoplanetList.NEAREST -> R.string.exoplanets_list_nearest
        ExoplanetList.EARTH_SIZED -> R.string.exoplanets_list_earth_sized
    }
