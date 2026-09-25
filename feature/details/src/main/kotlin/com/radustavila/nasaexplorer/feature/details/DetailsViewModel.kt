package com.radustavila.nasaexplorer.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.FetchApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeApod: ObserveApodUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val fetchApod: FetchApodUseCase,
) : ViewModel() {

    private val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<DetailsRoute>().date)

    // Starts "in flight" so an entry that has to be downloaded shows a spinner rather than flashing
    // the unavailable state between the first (empty) database emission and the request going out.
    private val localState = MutableStateFlow(LocalState(isFetching = true))

    val uiState: StateFlow<DetailsUiState> = combine(
        observeApod(date),
        observeConnectivity(),
        localState,
    ) { apod, isOnline, local ->
        when {
            apod != null -> DetailsUiState.Content(
                apod = apod,
                isOffline = !isOnline,
                error = local.error,
            )

            local.isFetching -> DetailsUiState.Loading

            else -> DetailsUiState.Unavailable(isOffline = !isOnline, error = local.error)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = DetailsUiState.Loading,
    )

    init {
        downloadIfMissing()
    }

    fun onToggleFavorite() {
        val current = uiState.value as? DetailsUiState.Content ?: return
        viewModelScope.launch {
            val result = toggleFavorite(current.apod)
            if (result is DataResult.Failure) {
                localState.update { it.copy(error = result.error.toUiError()) }
            }
        }
    }

    fun onErrorDismissed() {
        localState.update { it.copy(error = null) }
    }

    /** Retry targets *this* day: a feed sync would not reach an arbitrary archive date. */
    fun onRetry() {
        if (localState.value.isFetching) return
        localState.update { it.copy(isFetching = true, error = null) }
        viewModelScope.launch { download() }
    }

    /**
     * The feed only caches contiguous windows walking back from today, so a day reached from the
     * calendar is usually absent; it is downloaded on arrival instead of being reported as missing.
     */
    private fun downloadIfMissing() {
        viewModelScope.launch {
            if (observeApod(date).first() != null) {
                localState.update { it.copy(isFetching = false) }
                return@launch
            }
            download()
        }
    }

    private suspend fun download() {
        val result = fetchApod(date)
        localState.update {
            it.copy(
                isFetching = false,
                error = (result as? DataResult.Failure)?.error?.toUiError(),
            )
        }
    }

    /** State owned by the screen rather than by the database. */
    private data class LocalState(
        val isFetching: Boolean = false,
        val error: UiError? = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
