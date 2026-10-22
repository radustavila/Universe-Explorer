package com.radustavila.nasaexplorer.feature.skymap.render

import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
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
)

/** The words the map draws that are not in the catalogue, resolved from string resources. */
internal data class SkyNames(
    val bodies: Map<SolarSystemBody, String>,
    val iss: String,
    /** N, NE, E, SE, S, SW, W, NW. */
    val cardinals: List<String>,
)

/** Where [this] target is in the sky drawn by [sky] and [bodies], refracted; null when it is not there. */
internal fun SkyTarget.direction(sky: HorizontalSky, bodies: SkyMapBodies?): SkyVector? = when (this) {
    is SkyTarget.StarTarget -> if (index in 0 until sky.chart.starCount) sky.star(index) else null
    is SkyTarget.Constellation -> if (index in sky.chart.catalog.constellations.indices) sky.label(index) else null
    is SkyTarget.Body -> bodies?.solarSystem?.firstOrNull { it.body == body }?.let { SkyChart.apparent(it.horizontal) }
    is SkyTarget.Satellite -> bodies?.satellites?.firstOrNull { it.noradId == noradId }?.let { SkyChart.apparent(it.horizontal) }
}

/** Apparent direction of a Sun, Moon or planet. */
internal fun SolarSystemPosition.direction(): SkyVector = SkyChart.apparent(horizontal)
