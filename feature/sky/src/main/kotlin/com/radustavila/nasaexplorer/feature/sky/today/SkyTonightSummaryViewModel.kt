package com.radustavila.nasaexplorer.feature.sky.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyTonightUseCase
import com.radustavila.nasaexplorer.core.model.SkyTonight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class SkyTonightSummaryUiState(
    /** False until the saved location has been read, so the card does not flash its prompt. */
    val isLoaded: Boolean = false,
    /** Null without a saved location: the card asks for one instead. */
    val sky: SkyTonight? = null,
    /** Draw the Moon as seen from the southern hemisphere. */
    val southernHemisphere: Boolean = false,
)

/** Tonight's Moon, darkness and planets for the Today card; computed on the device, no network. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkyTonightSummaryViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    observeSkyTonight: ObserveSkyTonightUseCase,
) : ViewModel() {

    val uiState: StateFlow<SkyTonightSummaryUiState> = observeLocation()
        .map { it?.coordinate }
        .distinctUntilChanged()
        .flatMapLatest { coordinate ->
            if (coordinate == null) {
                flowOf(SkyTonightSummaryUiState(isLoaded = true))
            } else {
                observeSkyTonight(coordinate).map { sky ->
                    SkyTonightSummaryUiState(isLoaded = true, sky = sky, southernHemisphere = coordinate.latitude < 0.0)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = SkyTonightSummaryUiState(),
        )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
