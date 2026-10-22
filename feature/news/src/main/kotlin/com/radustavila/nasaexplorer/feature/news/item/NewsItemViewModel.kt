package com.radustavila.nasaexplorer.feature.news.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIsFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewsItemUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveFavoriteUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SaveNewsImageFavoriteUseCase
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.feature.news.NewsItemRoute
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
 * One news item, read from the cache (where favourite Images of the Day are never evicted). Only
 * Images of the Day can be saved: the rest is news that links out.
 */
@HiltViewModel
class NewsItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeItem: ObserveNewsItemUseCase,
    observeIsFavorite: ObserveIsFavoriteUseCase,
    private val saveFavorite: SaveNewsImageFavoriteUseCase,
    private val removeFavorite: RemoveFavoriteUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val id: String = savedStateHandle.toRoute<NewsItemRoute>().id
    private val error = MutableStateFlow<UiError?>(null)

    val uiState: StateFlow<NewsItemUiState> = combine(
        observeItem(id),
        observeIsFavorite(FavoriteSource.NASA_IMAGE_OF_THE_DAY, id),
        error,
    ) { item, isFavorite, error ->
        if (item == null) {
            NewsItemUiState.Unavailable
        } else {
            NewsItemUiState.Content(item = item, isFavorite = isFavorite, error = error, now = timeProvider.now())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = NewsItemUiState.Loading,
    )

    fun onToggleFavorite() {
        val current = uiState.value as? NewsItemUiState.Content ?: return
        if (!current.canBeSaved) return
        viewModelScope.launch {
            val result = if (current.isFavorite) {
                removeFavorite(FavoriteSource.NASA_IMAGE_OF_THE_DAY, current.item.id)
            } else {
                saveFavorite(current.item)
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

sealed interface NewsItemUiState {
    data object Loading : NewsItemUiState

    data class Content(
        val item: NewsItem,
        val isFavorite: Boolean,
        val error: UiError? = null,
        val now: Instant = Instant.EPOCH,
    ) : NewsItemUiState {
        val canBeSaved: Boolean get() = item.kind == NewsKind.IMAGE_OF_THE_DAY
    }

    /** Not in the cache: dropped from its feed and never saved. */
    data object Unavailable : NewsItemUiState
}
