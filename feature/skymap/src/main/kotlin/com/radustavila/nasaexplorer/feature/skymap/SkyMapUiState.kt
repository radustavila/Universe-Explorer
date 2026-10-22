package com.radustavila.nasaexplorer.feature.skymap

import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import java.time.Duration
import java.time.Instant

data class SkyMapUiState(
    /** False until the saved location has been read, so "no location" does not flash first. */
    val isLocationLoaded: Boolean = false,
    val location: ObserverLocation? = null,
    /** The stars and figures for [location] at [instant]; null while the catalogue loads. */
    val sky: HorizontalSky? = null,
    val bodies: SkyMapBodies? = null,
    /** The instant drawn: now plus [timeOffset]. */
    val instant: Instant? = null,
    val timeOffset: Duration = Duration.ZERO,
    val isNightMode: Boolean = false,
    val selected: SkyTarget? = null,
    /** IAU abbreviation of the constellation the selection is in, when it is a star, the Sun, the Moon or a planet. */
    val selectedConstellation: String? = null,
) {
    val isLive: Boolean get() = timeOffset.isZero
}

/** Something on the map that can be searched for, tapped and pointed at. */
sealed interface SkyTarget {

    /** A star of the catalogue, by its index (brightest first). */
    data class StarTarget(val index: Int) : SkyTarget

    data class Body(val body: SolarSystemBody) : SkyTarget

    /** A constellation figure, by its index in the catalogue (Serpens has two). */
    data class Constellation(val index: Int) : SkyTarget

    data class Satellite(val noradId: Int) : SkyTarget
}
