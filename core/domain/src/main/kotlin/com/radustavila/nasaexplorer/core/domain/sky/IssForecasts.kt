package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition

/** Upcoming ISS passes over the observer, predicted from the cached orbital elements. */
data class IssPassForecast(
    /** The elements the passes were computed from; null before the first refresh. */
    val elements: OrbitalElements?,
    /** Empty when there are no elements, or when they are too old to trust (see [isStale]). */
    val passes: List<SatellitePass>,
    /** The elements are too old for minute-accurate predictions; a refresh is needed. */
    val isStale: Boolean,
)

/** Where the ISS is now and the path it is on. */
data class IssTrack(
    val position: SatellitePosition?,
    /** Sub-satellite points from about one orbit behind to one orbit ahead; empty without elements. */
    val groundTrack: List<SatellitePosition>,
)
