package com.radustavila.nasaexplorer.feature.earthevents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOpenEarthEventsUseCase
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the events cache and nothing else: the periodic events sync keeps it fresh, so the card
 * works offline and costs no call by being shown.
 */
@HiltViewModel
class RightNowOnEarthViewModel @Inject constructor(
    observeOpenEvents: ObserveOpenEarthEventsUseCase,
) : ViewModel() {

    /** Open events per category, largest first; empty while nothing is cached. */
    val counts: StateFlow<List<Pair<EarthEventCategory, Int>>> = observeOpenEvents()
        .map(::countByCategory)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun countByCategory(events: List<EarthEvent>): List<Pair<EarthEventCategory, Int>> =
    events.groupingBy(EarthEvent::category).eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<EarthEventCategory, Int>> { it.second }.thenBy { it.first.ordinal })
