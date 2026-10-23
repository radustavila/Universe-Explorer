package com.radustavila.nasaexplorer.feature.news.podcast.episode

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePodcastEpisodeUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SavePodcastEpisodeFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.TogglePodcastDownloadUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.feature.news.PodcastEpisodeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One episode, read from the cache (where favourite and downloaded episodes are never evicted):
 * play it, keep it offline, save it.
 */
@HiltViewModel
class PodcastEpisodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeEpisode: ObservePodcastEpisodeUseCase,
    observeIsFavorite: ObserveIsFavoriteUseCase,
    private val saveFavorite: SavePodcastEpisodeFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
    private val toggleDownload: TogglePodcastDownloadUseCase,
    private val player: PodcastPlayer,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val id: String = savedStateHandle.toRoute<PodcastEpisodeRoute>().id
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<PodcastEpisodeUiState> = combine(
        observeEpisode(id),
        observeIsFavorite(FavoriteSource.NASA_PODCAST_EPISODE, id),
        player.playback,
        error,
    ) { episode, isFavorite, playback, error ->
        if (episode == null) {
            PodcastEpisodeUiState.Unavailable
        } else {
            PodcastEpisodeUiState.Content(
                episode = episode,
                isFavorite = isFavorite,
                playback = playback,
                error = error,
                now = timeProvider.now(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = PodcastEpisodeUiState.Loading,
    )

    fun onPlayClick() {
        val current = uiState.value as? PodcastEpisodeUiState.Content ?: return
        if (current.isPlaying) player.pause() else player.play(current.episode)
    }

    fun onToggleDownload() {
        val current = uiState.value as? PodcastEpisodeUiState.Content ?: return
        launchReportingErrors { toggleDownload(current.episode) }
    }

    fun onToggleFavorite() {
        val current = uiState.value as? PodcastEpisodeUiState.Content ?: return
        launchReportingErrors {
            if (current.isFavorite) {
                removeFavorite(FavoriteSource.NASA_PODCAST_EPISODE, current.episode.id)
            } else {
                saveFavorite(current.episode)
            }
        }
    }

    fun onErrorDismissed() {
        error.value = null
    }

    /** The player's error is shown here and by the mini player; dismissing it clears both. */
    fun onPlaybackErrorDismissed() {
        player.clearError()
    }

    private fun launchReportingErrors(action: suspend () -> DataResult<Unit>) {
        viewModelScope.launch {
            val result = action()
            if (result is DataResult.Failure) error.value = result.error.toUiError()
        }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface PodcastEpisodeUiState {
    data object Loading : PodcastEpisodeUiState

    data class Content(
        val episode: PodcastEpisode,
        val isFavorite: Boolean,
        val playback: PodcastPlayback = PodcastPlayback(),
        val error: UiError? = null,
        val now: Instant = Instant.EPOCH,
    ) : PodcastEpisodeUiState {
        val isLoaded: Boolean get() = playback.episode?.id == episode.id
        val isPlaying: Boolean get() = isLoaded && playback.isPlaying
    }

    /** Not in the cache: dropped from its feed, never saved or downloaded. */
    data object Unavailable : PodcastEpisodeUiState
}
