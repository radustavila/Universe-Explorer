package com.radustavila.nasaexplorer.feature.exoplanets

import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetList

data class ExoplanetsUiState(
    val selectedList: ExoplanetList = ExoplanetList.NEWEST,
    val planets: List<Exoplanet> = emptyList(),
    /** The selected list has not been read from the cache yet. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    /** No refresh has ever succeeded, so an empty list means "not downloaded yet". */
    val neverSynced: Boolean = true,
    val lastRefreshFailed: Boolean = false,
    val confirmedPlanets: Int? = null,
    /** First discovery year of the "newest" list. */
    val newestSinceYear: Int = 0,
)
