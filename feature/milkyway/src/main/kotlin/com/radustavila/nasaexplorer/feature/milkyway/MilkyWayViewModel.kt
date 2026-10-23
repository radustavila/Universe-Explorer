package com.radustavila.nasaexplorer.feature.milkyway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMilkyWayOutlookUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The planner and Sky tonight's card: the next 30 nights at the saved place, computed on the
 * device whenever the place changes and every quarter of an hour. Nothing goes to the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MilkyWayViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    private val observeOutlook: ObserveMilkyWayOutlookUseCase,
) : ViewModel() {

    val uiState: StateFlow<MilkyWayUiState> = observeLocation()
        .distinctUntilChanged { old, new -> old?.coordinate == new?.coordinate }
        .flatMapLatest { location ->
            if (location == null) {
                flowOf(MilkyWayUiState(isLocationLoaded = true))
            } else {
                observeOutlook(location.coordinate)
                    .map { MilkyWayUiState(isLocationLoaded = true, location = location, outlook = it) }
                    .onStart { emit(MilkyWayUiState(isLocationLoaded = true, location = location)) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = MilkyWayUiState(),
        )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
