package com.radustavila.nasaexplorer.feature.events.next

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingSkyEventsUseCase
import com.radustavila.nasaexplorer.core.model.SkyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class NextInTheSkyUiState(
    /** False until the first events are computed, so the card appears once, filled. */
    val isLoaded: Boolean = false,
    val events: List<SkyEvent> = emptyList(),
    val hasPlace: Boolean = false,
)

/** The next few sky events for the "Next in the sky" card; computed on the device, no network. */
@HiltViewModel
class NextInTheSkyViewModel @Inject constructor(
    observeUpcoming: ObserveUpcomingSkyEventsUseCase,
    observeLocation: ObserveObserverLocationUseCase,
) : ViewModel() {

    val uiState: StateFlow<NextInTheSkyUiState> = combine(observeUpcoming(), observeLocation()) { events, location ->
        NextInTheSkyUiState(isLoaded = true, events = events, hasPlace = location != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS), NextInTheSkyUiState())

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
