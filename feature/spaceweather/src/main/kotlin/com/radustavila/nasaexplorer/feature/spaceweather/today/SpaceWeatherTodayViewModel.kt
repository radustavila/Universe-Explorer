package com.radustavila.nasaexplorer.feature.spaceweather.today

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.Kp
import com.radustavila.nasaexplorer.core.model.KpReading
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class SpaceWeatherTodayUiState(
    /** False until the cache has been read, so the card does not flash its empty state. */
    val isLoaded: Boolean = false,
    /** Kp now: the 1-minute estimate, else the 3-hour slot under way; null while nothing is cached. */
    val kp: Double? = null,
    /** The forecast's highest slot in the next [PEAK_HORIZON], when it beats [kp]'s whole level. */
    val peak: KpReading? = null,
)

/** How far ahead the card looks for a stronger Kp: the horizon aurora alerts use. */
internal val PEAK_HORIZON: Duration = Duration.ofHours(24)

/**
 * The Kp card on Today, from the space weather cache. Showing it asks the space weather job for a
 * refresh without the aurora map (the job skips what is fresh), as the screen does with the map.
 */
@HiltViewModel
class SpaceWeatherTodayViewModel @Inject constructor(
    observeCurrentKp: ObserveCurrentKpUseCase,
    observeKpForecast: ObserveKpForecastUseCase,
    requestSync: RequestSpaceWeatherSyncUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<SpaceWeatherTodayUiState> = combine(
        observeCurrentKp(),
        observeKpForecast(),
    ) { current, forecast ->
        spaceWeatherTodayState(current, forecast, timeProvider.now())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SpaceWeatherTodayUiState(),
    )

    init {
        requestSync(includeAuroraMap = false)
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun spaceWeatherTodayState(
    current: CurrentKp?,
    forecast: List<KpReading>,
    now: Instant,
): SpaceWeatherTodayUiState {
    val latestSlot = forecast.filter { !it.start.isAfter(now) }.maxByOrNull { it.start }
    val kp = current?.kp ?: latestSlot?.kp
    val peak = forecast
        .filter { it.end.isAfter(now) && it.start.isBefore(now.plus(PEAK_HORIZON)) }
        .maxByOrNull { it.kp }
        ?.takeIf { kp == null || it.level > Kp.level(kp) }
    return SpaceWeatherTodayUiState(isLoaded = true, kp = kp, peak = peak)
}
