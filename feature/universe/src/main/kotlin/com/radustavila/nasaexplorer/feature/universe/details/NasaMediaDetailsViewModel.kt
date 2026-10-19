package com.radustavila.nasaexplorer.feature.universe.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchNasaMediaUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNasaMediaUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ResolveNasaMediaPlaybackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveNasaMediaFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.feature.universe.NasaMediaDetailsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One NASA library item. It is read from the repository (kept locally when it is on a rail or
 * saved, in memory when a search found it) and downloaded by id only when it is neither, e.g. after
 * process death.
 *
 * Videos and recordings resolve their playable file when they open; that needs a connection, and
 * is retried when one comes back.
 */
@HiltViewModel
class NasaMediaDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeMedia: ObserveNasaMediaUseCase,
    observeIsFavorite: ObserveIsFavoriteUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val fetchMedia: FetchNasaMediaUseCase,
    private val resolvePlayback: ResolveNasaMediaPlaybackUseCase,
    private val saveFavorite: SaveNasaMediaFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
) : ViewModel() {

    private val nasaId: String = savedStateHandle.toRoute<NasaMediaDetailsRoute>().nasaId
    private val media = observeMedia(nasaId)
    private val isOnline = observeConnectivity()

    // Starts in flight, so an item that has to be downloaded shows a spinner rather than flashing
    // the unavailable state first.
    private val localState = MutableStateFlow(LocalState(isFetching = true))

    val uiState: StateFlow<NasaMediaDetailsUiState> = combine(
        media,
        observeIsFavorite(FavoriteSource.NASA_LIBRARY, nasaId),
        isOnline,
        localState,
    ) { media, isFavorite, online, local ->
        when {
            media != null -> NasaMediaDetailsUiState.Content(
                media = media,
                isFavorite = isFavorite,
                isOffline = !online,
                playback = local.playback,
                error = local.error,
            )

            local.isFetching -> NasaMediaDetailsUiState.Loading

            else -> NasaMediaDetailsUiState.Unavailable(isOffline = !online, error = local.error)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = NasaMediaDetailsUiState.Loading,
    )

    init {
        viewModelScope.launch {
            if (media.first() == null) download() else localState.update { it.copy(isFetching = false) }
        }
        viewModelScope.launch {
            val item = media.filterNotNull().first()
            if (item.isPlayable) resolve(item)
        }
        viewModelScope.launch {
            // Back online: finish whatever needed the connection.
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect {
                val current = uiState.value
                when {
                    current is NasaMediaDetailsUiState.Unavailable -> onRetry()
                    current is NasaMediaDetailsUiState.Content &&
                        current.playback is PlaybackState.NeedsConnection -> resolve(current.media)
                }
            }
        }
    }

    fun onToggleFavorite() {
        val current = uiState.value as? NasaMediaDetailsUiState.Content ?: return
        viewModelScope.launch {
            val result = if (current.isFavorite) {
                removeFavorite(FavoriteSource.NASA_LIBRARY, current.media.nasaId)
            } else {
                saveFavorite(current.media)
            }
            if (result is DataResult.Failure) {
                localState.update { it.copy(error = result.error.toUiError()) }
            }
        }
    }

    fun onErrorDismissed() {
        localState.update { it.copy(error = null) }
    }

    fun onRetry() {
        if (localState.value.isFetching) return
        localState.update { it.copy(isFetching = true, error = null) }
        viewModelScope.launch { download() }
    }

    fun onRetryPlayback() {
        val current = uiState.value as? NasaMediaDetailsUiState.Content ?: return
        viewModelScope.launch { resolve(current.media) }
    }

    private suspend fun download() {
        val result = fetchMedia(nasaId)
        localState.update {
            it.copy(isFetching = false, error = (result as? DataResult.Failure)?.error?.toUiError())
        }
    }

    private suspend fun resolve(item: NasaMedia) {
        if (localState.value.playback == PlaybackState.Resolving) return
        localState.update { it.copy(playback = PlaybackState.Resolving) }
        val playback = when (val result = resolvePlayback(item)) {
            is DataResult.Success -> PlaybackState.Ready(result.data)
            is DataResult.Failure -> when (result.error) {
                AppError.NoConnection -> PlaybackState.NeedsConnection
                else -> PlaybackState.Failed(result.error.toUiError())
            }
        }
        localState.update { it.copy(playback = playback) }
    }

    /** State owned by the screen rather than by the repository. */
    private data class LocalState(
        val isFetching: Boolean = false,
        val playback: PlaybackState = PlaybackState.Idle,
        val error: UiError? = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
