package com.radustavila.nasaexplorer.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveFavoriteApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ToggleFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.Apod
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    observeFavorites: ObserveFavoriteApodsUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val transientError = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<FavoritesUiState> = combine(
        observeFavorites(),
        transientError,
    ) { favorites, error ->
        FavoritesUiState(items = favorites, isLoading = false, error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = FavoritesUiState(),
    )

    fun onToggleFavorite(apod: Apod) {
        viewModelScope.launch {
            val result = toggleFavorite(apod)
            if (result is DataResult.Failure) {
                transientError.update { result.error.toUiError() }
            }
        }
    }

    fun onErrorDismissed() {
        transientError.update { null }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
