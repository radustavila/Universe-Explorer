package com.radustavila.nasaexplorer.feature.sky.stargazing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveStargazingForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshCloudForecastUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.StargazingForecast
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class StargazingUiState(
    /** False until the saved location has been read, so nothing flashes. */
    val isLoaded: Boolean = false,
    val hasLocation: Boolean = false,
    /** Null while the first nights are computed. */
    val forecast: StargazingForecast? = null,
    val freshness: DataFreshness = DataFreshness(),
)

/**
 * The stargazing outlook for the saved place, for the Sky tonight card and the Today line.
 *
 * Each time the place changes or a card starts watching, the forecast is refreshed; the repository
 * only goes to MET Norway once the stored copy has expired, so this costs nothing most times. The
 * outlook itself is computed from the cache and keeps showing offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StargazingViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    observeForecast: ObserveStargazingForecastUseCase,
    private val refreshForecast: RefreshCloudForecastUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
) : ViewModel() {

    private val lastRefreshFailed = MutableStateFlow(false)

    private val outlook = observeLocation()
        .map { it?.coordinate }
        .distinctUntilChanged()
        .flatMapLatest { coordinate ->
            if (coordinate == null) {
                flowOf(StargazingUiState(isLoaded = true))
            } else {
                channelFlow {
                    launch { lastRefreshFailed.value = refreshForecast(coordinate).failed() }
                    send(StargazingUiState(isLoaded = true, hasLocation = true))
                    observeForecast(coordinate).collect { forecast ->
                        send(StargazingUiState(isLoaded = true, hasLocation = true, forecast = forecast))
                    }
                }
            }
        }

    val uiState: StateFlow<StargazingUiState> = combine(
        outlook,
        observeConnectivity(),
        lastRefreshFailed,
    ) { state, isOnline, failed ->
        state.copy(
            freshness = DataFreshness(
                lastUpdatedAt = state.forecast?.fetchedAt,
                isOffline = !isOnline,
                lastRefreshFailed = failed,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = StargazingUiState(),
    )

    private fun SourceRefreshResult<Unit>.failed(): Boolean =
        this is SourceRefreshResult.Completed && result is DataResult.Failure

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
