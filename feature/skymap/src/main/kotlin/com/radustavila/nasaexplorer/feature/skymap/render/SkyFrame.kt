package com.radustavila.nasaexplorer.feature.skymap.render

import com.radustavila.nasaexplorer.core.model.CometSkyPosition
import com.radustavila.nasaexplorer.core.model.GalacticCentre
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.cometShortName
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.core.skymap.SkyLens
import com.radustavila.nasaexplorer.core.skymap.SkyVector
import com.radustavila.nasaexplorer.core.skymap.SkyView
import com.radustavila.nasaexplorer.feature.skymap.SkyTarget

/** Everything one frame of the map shows. */
internal class SkyFrame(
    val sky: HorizontalSky,
    val bodies: SkyMapBodies?,
    val view: SkyView,
    val fieldOfViewDegrees: Double,
    val selected: SkyTarget?,
    val palette: SkyPalette,
    val names: SkyNames,
    val lens: SkyLens = SkyLens.STEREOGRAPHIC,
    /** The deep-sky layer (F23), switched in the layers menu. */
    val showDeepSky: Boolean = false,
)

/** The words the map draws that are not in the catalogue, resolved from string resources. */
internal data class SkyNames(
    val bodies: Map<SolarSystemBody, String>,
    /** The stations' everyday names by NORAD id; others go by their catalogue name. */
    val satellites: Map<Int, String>,
    /** N, NE, E, SE, S, SW, W, NW. */
    val cardinals: List<String>,
    /** The core's two names: on the map, and the other one search also finds it by. */
    val galacticCentre: String = "",
    val milkyWayCore: String = "",
)

/** Where [this] target is in the sky drawn by [sky] and [bodies], refracted; null when it is not there. */
internal fun SkyTarget.direction(sky: HorizontalSky, bodies: SkyMapBodies?): SkyVector? = when (this) {
    is SkyTarget.StarTarget -> if (index in 0 until sky.chart.starCount) sky.star(index) else null
    is SkyTarget.Constellation -> if (index in sky.chart.catalog.constellations.indices) sky.label(index) else null
    is SkyTarget.Body -> bodies?.solarSystem?.firstOrNull { it.body == body }
        ?.let { SkyChart.apparent(it.horizontal) }
    is SkyTarget.Satellite -> bodies?.satellites?.firstOrNull { it.noradId == noradId }
        ?.let { SkyChart.apparent(it.horizontal) }
    is SkyTarget.Comet -> bodies?.comet(designation)?.let { SkyChart.apparent(it.horizontal) }
    SkyTarget.GalacticCentre -> sky.direction(GalacticCentre.POSITION)
    is SkyTarget.DeepSky -> sky.chart.deepSkyIndex(id).takeIf { it >= 0 }?.let(sky::deepSky)
}

/** The deep-sky object [target] names, when the catalogue has it. */
internal fun HorizontalSky.deepSkyObject(target: SkyTarget.DeepSky) =
    chart.deepSkyIndex(target.id).takeIf { it >= 0 }?.let { chart.catalog.deepSky[it] }

/** The comet [designation] names, when it is on the map. */
internal fun SkyMapBodies.comet(designation: String): CometSkyPosition? =
    comets.firstOrNull { it.designation == designation }

/** "Bok" for "C/2026 A2 (Bok)": short enough for the map. */
internal fun CometSkyPosition.shortName(): String = cometShortName(name)

/** "ISS", "Hubble", or the catalogue's name for a satellite without an everyday one. */
internal fun SkyNames.satellite(noradId: Int, bodies: SkyMapBodies?): String =
    satellites[noradId] ?: bodies?.satellites?.firstOrNull { it.noradId == noradId }?.name.orEmpty()

/** Apparent direction of a Sun, Moon or planet. */
internal fun SolarSystemPosition.direction(): SkyVector = SkyChart.apparent(horizontal)
