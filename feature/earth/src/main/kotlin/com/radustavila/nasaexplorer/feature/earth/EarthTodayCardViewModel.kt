package com.radustavila.nasaexplorer.feature.earth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEpicSyncStateUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEpicUseCase
import com.radustavila.nasaexplorer.core.model.EpicImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the Today card shows: the newest natural-colour day, or that it is on its way. */
data class EarthTodayCardUiState(
    val images: List<EpicImage> = emptyList(),
    val isLoading: Boolean = false,
) {
    /** The frame the card shows: the middle of the day, when the disc is usually best lit for most viewers. */
    val coverImage: EpicImage? get() = images.getOrNull(images.size / 2)
}

/**
 * Backs the small Earth card on Today. Owned by this feature and scoped to whichever screen hosts
 * the card, so the hosting feature needs to know nothing about EPIC.
 */
@HiltViewModel
class EarthTodayCardViewModel @Inject constructor(
    observeLatestEpicDay: ObserveLatestEpicDayUseCase,
    observeEpicSyncState: ObserveEpicSyncStateUseCase,
    refreshEpic: RefreshEpicUseCase,
) : ViewModel() {

    val uiState: StateFlow<EarthTodayCardUiState> = combine(
        observeLatestEpicDay(),
        observeEpicSyncState(),
    ) { images, syncState ->
        EarthTodayCardUiState(images = images, isLoading = images.isEmpty() && syncState.isRunning)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = EarthTodayCardUiState(),
    )

    init {
        // Shares the unique EPIC work: a no-op while a sync is already queued or running.
        refreshEpic()
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
