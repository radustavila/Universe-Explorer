package com.radustavila.nasaexplorer.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshFeedUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeApod: ObserveApodUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val refreshFeed: RefreshFeedUseCase,
) : ViewModel() {

    private val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<DetailsRoute>().date)

    private val transientError = MutableStateFlow<com.radustavila.nasaexplorer.core.designsystem.error.UiError?>(null)

    val uiState: StateFlow<DetailsUiState> = combine(
        observeApod(date),
        observeConnectivity(),
        transientError,
    ) { apod, isOnline, error ->
        when (apod) {
            null -> DetailsUiState.Unavailable(isOffline = !isOnline)
            else -> DetailsUiState.Content(apod = apod, isOffline = !isOnline, error = error)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = DetailsUiState.Loading,
    )

    fun onToggleFavorite() {
        val current = uiState.value as? DetailsUiState.Content ?: return
        viewModelScope.launch {
            val result = toggleFavorite(current.apod)
            if (result is DataResult.Failure) {
                transientError.update { result.error.toUiError() }
            }
        }
    }

    fun onErrorDismissed() {
        transientError.update { null }
    }

    fun onRetry() = refreshFeed()

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
