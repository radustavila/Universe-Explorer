package com.radustavila.nasaexplorer.feature.details

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.Apod

sealed interface DetailsUiState {
    data object Loading : DetailsUiState

    data class Content(
        val apod: Apod,
        val isOffline: Boolean = false,
        val error: UiError? = null,
    ) : DetailsUiState

    /**
     * The entry is neither cached nor downloadable. Offline this is terminal until connectivity
     * returns, which is why the two cases are distinguished.
     */
    data class Unavailable(
        val isOffline: Boolean,
        val error: UiError? = null,
    ) : DetailsUiState
}
