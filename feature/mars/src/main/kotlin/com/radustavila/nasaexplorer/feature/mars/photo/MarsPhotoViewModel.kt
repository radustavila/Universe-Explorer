package com.radustavila.nasaexplorer.feature.mars.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMarsPhotoUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveMarsPhotoFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.feature.mars.MarsPhotoRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rover photo, read from the cache (where favourites are never evicted). */
@HiltViewModel
class MarsPhotoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePhoto: ObserveMarsPhotoUseCase,
    observeIsFavorite: ObserveIsFavoriteUseCase,
    private val saveFavorite: SaveMarsPhotoFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
) : ViewModel() {

    private val id: String = savedStateHandle.toRoute<MarsPhotoRoute>().id
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<MarsPhotoUiState> = combine(
        observePhoto(id),
        observeIsFavorite(FavoriteSource.MARS, id),
        error,
    ) { photo, isFavorite, error ->
        if (photo == null) {
            MarsPhotoUiState.Unavailable
        } else {
            MarsPhotoUiState.Content(photo = photo, isFavorite = isFavorite, error = error)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = MarsPhotoUiState.Loading,
    )

    fun onToggleFavorite() {
        val current = uiState.value as? MarsPhotoUiState.Content ?: return
        viewModelScope.launch {
            val result = if (current.isFavorite) {
                removeFavorite(FavoriteSource.MARS, current.photo.id)
            } else {
                saveFavorite(current.photo)
            }
            if (result is DataResult.Failure) error.value = result.error.toUiError()
        }
    }

    fun onErrorDismissed() {
        error.value = null
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface MarsPhotoUiState {
    data object Loading : MarsPhotoUiState

    data class Content(
        val photo: MarsPhoto,
        val isFavorite: Boolean,
        val error: UiError? = null,
    ) : MarsPhotoUiState

    /** Not in the cache (evicted, and never saved). */
    data object Unavailable : MarsPhotoUiState
}
