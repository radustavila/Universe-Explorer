package com.radustavila.nasaexplorer.feature.exoplanets.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveExoplanetUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.feature.exoplanets.ExoplanetDetailsRoute
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
class ExoplanetDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeExoplanet: ObserveExoplanetUseCase,
    observeIsFavorite: ObserveIsFavoriteUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val saveFavorite: SaveFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
) : ViewModel() {

    private val name: String = savedStateHandle.toRoute<ExoplanetDetailsRoute>().name

    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<ExoplanetDetailsUiState> = combine(
        observeExoplanet(name),
        observeIsFavorite(FavoriteSource.EXOPLANET, name),
        observeConnectivity(),
        error,
    ) { planet, isFavorite, isOnline, currentError ->
        if (planet == null) {
            ExoplanetDetailsUiState.NotFound(name)
        } else {
            ExoplanetDetailsUiState.Content(
                planet = planet,
                isFavorite = isFavorite,
                isOffline = !isOnline,
                error = currentError,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = ExoplanetDetailsUiState.Loading,
    )

    fun onToggleFavorite() {
        val content = uiState.value as? ExoplanetDetailsUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isFavorite) {
                removeFavorite(FavoriteSource.EXOPLANET, content.planet.name)
            } else {
                // Planets have no picture: the Favourites card shows its placeholder.
                saveFavorite(
                    source = FavoriteSource.EXOPLANET,
                    itemId = content.planet.name,
                    title = content.planet.name,
                    imageUrl = null,
                )
            }
            if (result is DataResult.Failure) error.value = result.error.toUiError()
        }
    }

    fun onErrorDismissed() {
        error.update { null }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
