package com.radustavila.nasaexplorer.feature.events.component

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.Apparition
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.PlanetTransit
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SeasonMarker
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import com.radustavila.nasaexplorer.feature.events.R
import java.time.Instant
import kotlin.math.roundToInt

internal val SkyEventCategory.icon: ImageVector
    get() = when (this) {
        SkyEventCategory.ECLIPSES -> Icons.Outlined.Contrast
        SkyEventCategory.MOON -> Icons.Outlined.DarkMode
        SkyEventCategory.PLANETS -> Icons.Outlined.Public
        SkyEventCategory.METEORS -> Icons.Outlined.AutoAwesome
        SkyEventCategory.COMETS -> Icons.Outlined.Flare
        SkyEventCategory.SEASONS -> Icons.Outlined.WbSunny
    }

@get:StringRes
internal val SkyEventCategory.labelRes: Int
    get() = when (this) {
        SkyEventCategory.ECLIPSES -> R.string.sky_events_category_eclipses
        SkyEventCategory.MOON -> R.string.sky_events_category_moon
        SkyEventCategory.PLANETS -> R.string.sky_events_category_planets
        SkyEventCategory.METEORS -> R.string.sky_events_category_meteors
        SkyEventCategory.COMETS -> R.string.sky_events_category_comets
        SkyEventCategory.SEASONS -> R.string.sky_events_category_seasons
    }

/** The moment a list shows for an event: the local maximum of an eclipse seen from here, else its time. */
internal val SkyEvent.listedTime: Instant
    get() = (this as? SolarEclipse)?.local?.peak ?: time

/**
 * One short line under an event's name in a list, or null when the name says it all (a quarter
 * Moon). [hasPlace] tells "not visible from here" from "no place to tell".
 */
@Composable
@ReadOnlyComposable
internal fun skyEventSummary(event: SkyEvent, hasPlace: Boolean, units: UnitFormatter): String? = when (event) {
    is SolarEclipse -> solarEclipseSummary(event, hasPlace)
    is LunarEclipse -> lunarEclipseSummary(event)
    is MoonPhaseEvent -> if (event.quarter == MoonQuarter.FULL_MOON) {
        stringResource(R.string.sky_events_moon_distance, units.distance(event.distanceKm))
    } else {
        null
    }

    is PlanetOpposition -> stringResource(
        R.string.sky_events_planet_details,
        units.number(event.magnitude, 1),
        event.constellation,
    )

    is GreatestElongation -> stringResource(event.apparition.labelRes)
    is PlanetTransit -> stringResource(R.string.sky_events_not_visible_here).takeIf { event.isVisibleLocally == false }
    is SeasonEvent -> stringResource(event.marker.meaningRes)
    is MeteorShowerPeak -> stringResource(
        R.string.sky_events_meteor_summary,
        event.zenithalHourlyRate,
        event.moonIlluminatedFraction.percent(),
    )

    is CometPerihelion -> event.magnitude
        ?.let { stringResource(R.string.sky_events_comet_magnitude, units.number(it, 1)) }
        ?: stringResource(R.string.sky_events_comet_sun_distance, units.number(event.perihelionDistanceAu, 2))
}

@Composable
@ReadOnlyComposable
private fun solarEclipseSummary(event: SolarEclipse, hasPlace: Boolean): String {
    val local = event.local
    return when {
        local == null && hasPlace -> stringResource(R.string.sky_events_not_visible_here)
        local == null -> stringResource(R.string.sky_events_solar_somewhere)
        local.type == SolarEclipseType.TOTAL -> stringResource(R.string.sky_events_solar_local_total)
        local.type == SolarEclipseType.ANNULAR -> stringResource(R.string.sky_events_solar_local_annular)
        else -> stringResource(R.string.sky_events_solar_local_partial, local.obscuration.percent())
    }
}

@Composable
@ReadOnlyComposable
private fun lunarEclipseSummary(event: LunarEclipse): String = when (event.local?.isVisible) {
    true -> stringResource(R.string.sky_events_lunar_visible_here)
    false -> stringResource(R.string.sky_events_not_visible_here)
    null -> stringResource(R.string.sky_events_lunar_somewhere)
}

@get:StringRes
internal val Apparition.labelRes: Int
    get() = when (this) {
        Apparition.EVENING -> R.string.sky_events_elongation_evening
        Apparition.MORNING -> R.string.sky_events_elongation_morning
    }

@get:StringRes
internal val SeasonMarker.meaningRes: Int
    get() = when (this) {
        SeasonMarker.MARCH_EQUINOX -> R.string.sky_events_march_equinox_meaning
        SeasonMarker.JUNE_SOLSTICE -> R.string.sky_events_june_solstice_meaning
        SeasonMarker.SEPTEMBER_EQUINOX -> R.string.sky_events_september_equinox_meaning
        SeasonMarker.DECEMBER_SOLSTICE -> R.string.sky_events_december_solstice_meaning
    }

internal fun Double.percent(): Int = (this * PERCENT).roundToInt().coerceIn(0, PERCENT)

@StringRes
internal fun compassPoint(azimuthDegrees: Double): Int {
    val sector = FULL_TURN / COMPASS_POINTS.size
    val normalized = (azimuthDegrees % FULL_TURN + FULL_TURN) % FULL_TURN
    val index = ((normalized + sector / 2) / sector).toInt() % COMPASS_POINTS.size
    return COMPASS_POINTS[index]
}

private const val PERCENT = 100
private const val FULL_TURN = 360.0

private val COMPASS_POINTS = intArrayOf(
    R.string.sky_events_compass_n,
    R.string.sky_events_compass_ne,
    R.string.sky_events_compass_e,
    R.string.sky_events_compass_se,
    R.string.sky_events_compass_s,
    R.string.sky_events_compass_sw,
    R.string.sky_events_compass_w,
    R.string.sky_events_compass_nw,
)
