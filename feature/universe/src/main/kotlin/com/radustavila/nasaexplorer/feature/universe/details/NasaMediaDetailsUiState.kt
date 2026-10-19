package com.radustavila.nasaexplorer.feature.universe.details

import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.model.NasaMedia

sealed interface NasaMediaDetailsUiState {
    data object Loading : NasaMediaDetailsUiState

    data class Content(
        val media: NasaMedia,
        val isFavorite: Boolean,
        val isOffline: Boolean = false,
        /** Only meaningful for videos and recordings. */
        val playback: PlaybackState = PlaybackState.Idle,
        val error: UiError? = null,
    ) : NasaMediaDetailsUiState

    /** Neither kept locally nor downloadable right now. */
    data class Unavailable(
        val isOffline: Boolean,
        val error: UiError? = null,
    ) : NasaMediaDetailsUiState
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Resolving : PlaybackState
    data class Ready(val url: String) : PlaybackState

    /** The file list could not be fetched without a connection. */
    data object NeedsConnection : PlaybackState
    data class Failed(val error: UiError) : PlaybackState
}
