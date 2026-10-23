package com.radustavila.nasaexplorer.core.designsystem.skyevent

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.radustavila.nasaexplorer.core.designsystem.R
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipseType
import com.radustavila.nasaexplorer.core.model.MeteorShower
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.MoonSize
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.PlanetTransit
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SeasonMarker
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import com.radustavila.nasaexplorer.core.model.cometShortName

/**
 * The name of a sky event ("Total solar eclipse", "Perseids peak", "Mars at opposition", "Comet
 * Bok at perihelion"), shared by
 * the calendar screen, the reminder notification and universal search so they all say the same.
 * Works from [Resources], so code outside composition (a notification, a search source) can use it.
 */
class SkyEventText(private val resources: Resources) {

    fun title(event: SkyEvent): String = when (event) {
        is SolarEclipse -> string(event.type.solarTitle())
        is LunarEclipse -> string(event.type.lunarTitle())
        is MoonPhaseEvent -> string(event.moonTitle())
        is PlanetOpposition -> string(R.string.sky_event_opposition, planet(event.planet))
        is GreatestElongation -> string(R.string.sky_event_elongation, planet(event.planet))
        is PlanetTransit -> string(R.string.sky_event_transit, planet(event.planet))
        is SeasonEvent -> string(event.marker.title())
        is MeteorShowerPeak -> string(R.string.sky_event_meteor_peak, shower(event.shower))
        is CometPerihelion -> string(R.string.sky_event_comet_perihelion, cometShortName(event.name))
    }

    fun planet(planet: Planet): String = string(
        when (planet) {
            Planet.MERCURY -> R.string.sky_event_planet_mercury
            Planet.VENUS -> R.string.sky_event_planet_venus
            Planet.MARS -> R.string.sky_event_planet_mars
            Planet.JUPITER -> R.string.sky_event_planet_jupiter
            Planet.SATURN -> R.string.sky_event_planet_saturn
        },
    )

    fun shower(shower: MeteorShower): String = string(
        when (shower) {
            MeteorShower.QUADRANTIDS -> R.string.sky_event_shower_quadrantids
            MeteorShower.LYRIDS -> R.string.sky_event_shower_lyrids
            MeteorShower.ETA_AQUARIIDS -> R.string.sky_event_shower_eta_aquariids
            MeteorShower.SOUTHERN_DELTA_AQUARIIDS -> R.string.sky_event_shower_southern_delta_aquariids
            MeteorShower.PERSEIDS -> R.string.sky_event_shower_perseids
            MeteorShower.DRACONIDS -> R.string.sky_event_shower_draconids
            MeteorShower.ORIONIDS -> R.string.sky_event_shower_orionids
            MeteorShower.LEONIDS -> R.string.sky_event_shower_leonids
            MeteorShower.GEMINIDS -> R.string.sky_event_shower_geminids
            MeteorShower.URSIDS -> R.string.sky_event_shower_ursids
        },
    )

    private fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)
}

/** [SkyEventText.title] inside composition. */
@Composable
@ReadOnlyComposable
fun skyEventTitle(event: SkyEvent): String = SkyEventText(LocalResources.current).title(event)

@Composable
@ReadOnlyComposable
fun planetName(planet: Planet): String = SkyEventText(LocalResources.current).planet(planet)

@Composable
@ReadOnlyComposable
fun meteorShowerName(shower: MeteorShower): String = SkyEventText(LocalResources.current).shower(shower)

private fun SolarEclipseType.solarTitle(): Int = when (this) {
    SolarEclipseType.TOTAL -> R.string.sky_event_solar_eclipse_total
    SolarEclipseType.ANNULAR -> R.string.sky_event_solar_eclipse_annular
    SolarEclipseType.PARTIAL -> R.string.sky_event_solar_eclipse_partial
}

private fun LunarEclipseType.lunarTitle(): Int = when (this) {
    LunarEclipseType.TOTAL -> R.string.sky_event_lunar_eclipse_total
    LunarEclipseType.PARTIAL -> R.string.sky_event_lunar_eclipse_partial
    LunarEclipseType.PENUMBRAL -> R.string.sky_event_lunar_eclipse_penumbral
}

private fun MoonPhaseEvent.moonTitle(): Int = when (quarter) {
    MoonQuarter.NEW_MOON -> R.string.sky_event_new_moon
    MoonQuarter.FIRST_QUARTER -> R.string.sky_event_first_quarter
    MoonQuarter.LAST_QUARTER -> R.string.sky_event_last_quarter
    MoonQuarter.FULL_MOON -> when (size) {
        MoonSize.SUPERMOON -> R.string.sky_event_supermoon
        MoonSize.MICROMOON -> R.string.sky_event_micromoon
        null -> R.string.sky_event_full_moon
    }
}

private fun SeasonMarker.title(): Int = when (this) {
    SeasonMarker.MARCH_EQUINOX -> R.string.sky_event_march_equinox
    SeasonMarker.JUNE_SOLSTICE -> R.string.sky_event_june_solstice
    SeasonMarker.SEPTEMBER_EQUINOX -> R.string.sky_event_september_equinox
    SeasonMarker.DECEMBER_SOLSTICE -> R.string.sky_event_december_solstice
}
