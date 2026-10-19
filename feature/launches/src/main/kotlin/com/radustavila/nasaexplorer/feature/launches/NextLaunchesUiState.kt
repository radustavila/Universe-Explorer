package com.radustavila.nasaexplorer.feature.launches

import com.radustavila.nasaexplorer.core.model.Launch

/** Cached upcoming launches, soonest first. The card picks what is still ahead at draw time. */
data class NextLaunchesUiState(
    val launches: List<Launch> = emptyList(),
)
