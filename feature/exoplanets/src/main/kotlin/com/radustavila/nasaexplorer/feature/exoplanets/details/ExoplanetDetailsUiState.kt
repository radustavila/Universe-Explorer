package com.radustavila.nasaexplorer.feature.exoplanets.details

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.Exoplanet

sealed interface ExoplanetDetailsUiState {
    data object Loading : ExoplanetDetailsUiState

    data class Content(
        val planet: Exoplanet,
        val isFavorite: Boolean = false,
        val isOffline: Boolean = false,
        val error: UiError? = null,
    ) : ExoplanetDetailsUiState

    /** Not in the cache: a favourite opened on a device that never downloaded the catalogue. */
    data class NotFound(val name: String) : ExoplanetDetailsUiState
}
