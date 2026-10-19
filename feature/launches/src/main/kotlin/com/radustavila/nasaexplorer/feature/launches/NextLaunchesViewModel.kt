package com.radustavila.nasaexplorer.feature.launches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingLaunchesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the launches cache and nothing else: refreshing it is the sync worker's job, so the card
 * works offline and never spends Launch Library 2's small hourly budget by being shown.
 */
@HiltViewModel
class NextLaunchesViewModel @Inject constructor(
    observeUpcomingLaunches: ObserveUpcomingLaunchesUseCase,
) : ViewModel() {

    val uiState: StateFlow<NextLaunchesUiState> = observeUpcomingLaunches()
        .map(::NextLaunchesUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = NextLaunchesUiState(),
        )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
