package com.radustavila.nasaexplorer.feature.sky.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPositionUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class IssNowUiState(
    /** False until the first values arrive, so the card does not flash an empty state. */
    val isLoaded: Boolean = false,
    /** Propagated from the cached orbit; null before it is downloaded or once it is too old. */
    val position: SatellitePosition? = null,
    val nextPass: IssNextPass = IssNextPass.NoPlace,
)

/** The line under the position: the next visible pass over the user, or why there is none. */
sealed interface IssNextPass {
    /** No saved location, so no passes can be predicted. */
    data object NoPlace : IssNextPass

    /** No usable orbit; the position line already says so. */
    data object NoOrbit : IssNextPass

    /** None visible within the forecast window. */
    data object NoneVisible : IssNextPass

    data class Next(val pass: SatellitePass) : IssNextPass
}

/**
 * The ISS card on Today: where the station is now and its next visible pass over the saved place,
 * all computed on the device from the cached orbit. Showing the card refreshes the orbit when it is
 * due (CelesTrak's twice-a-day limit, shared with Sky tonight), so it works without ever opening
 * that screen; it never polls the live tracker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IssNowViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    observeIssPasses: ObserveIssPassesUseCase,
    observeIssPosition: ObserveIssPositionUseCase,
    private val refreshElements: RefreshOrbitalElementsUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val nextPass = observeLocation()
        .map { it?.coordinate }
        .distinctUntilChanged()
        .flatMapLatest { coordinate ->
            if (coordinate == null) {
                flowOf(IssNextPass.NoPlace)
            } else {
                observeIssPasses(coordinate).map { it.nextVisiblePass(timeProvider.now()) }
            }
        }

    val uiState: StateFlow<IssNowUiState> = combine(observeIssPosition(), nextPass) { position, pass ->
        IssNowUiState(isLoaded = true, position = position, nextPass = pass)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = IssNowUiState(),
    )

    init {
        viewModelScope.launch { refreshElements() }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun IssPassForecast.nextVisiblePass(now: Instant): IssNextPass {
    if (elements == null || isStale) return IssNextPass.NoOrbit
    return passes.firstOrNull { it.isVisible && it.end.isAfter(now) }
        ?.let(IssNextPass::Next)
        ?: IssNextPass.NoneVisible
}
