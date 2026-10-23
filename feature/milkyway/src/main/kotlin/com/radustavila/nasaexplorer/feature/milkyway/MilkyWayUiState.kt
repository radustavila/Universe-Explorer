package com.radustavila.nasaexplorer.feature.milkyway

import com.radustavila.nasaexplorer.core.model.MilkyWayOutlook
import com.radustavila.nasaexplorer.core.model.ObserverLocation

data class MilkyWayUiState(
    /** False until the saved place has been read, so "no place" does not flash first. */
    val isLocationLoaded: Boolean = false,
    val location: ObserverLocation? = null,
    /** The coming nights at [location]; null without one or while they are computed. */
    val outlook: MilkyWayOutlook? = null,
)
