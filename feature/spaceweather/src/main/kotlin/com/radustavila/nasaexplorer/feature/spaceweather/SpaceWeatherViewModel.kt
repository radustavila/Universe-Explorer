package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSolarActivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSpaceWeatherSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraOval
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Kp, its forecast, the aurora map, the Sun's latest flare and CME, and the alert settings, all from
 * the local cache. Opening the screen asks the space weather job for a refresh including the aurora
 * grid (the job skips what is fresh) and keeps the 30-minute background refresh registered.
 */
@HiltViewModel
class SpaceWeatherViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeKpForecast: ObserveKpForecastUseCase,
    observeCurrentKp: ObserveCurrentKpUseCase,
    observeAuroraForecast: ObserveAuroraForecastUseCase,
    observeSolarActivity: ObserveSolarActivityUseCase,
    observeAlertSettings: ObserveAuroraAlertSettingsUseCase,
    observeSyncStatus: ObserveSpaceWeatherSyncStatusUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val requestSync: RequestSpaceWeatherSyncUseCase,
    private val setAlertEnabled: SetAuroraAlertEnabledUseCase,
    private val setAlertLocation: SetAuroraAlertLocationUseCase,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val hemisphere = savedStateHandle.getStateFlow(KEY_HEMISPHERE, Hemisphere.North.name)
        .map { name -> Hemisphere.entries.firstOrNull { it.name == name } ?: Hemisphere.North }

    /** Contouring 65,000 grid points is quick, but not main-thread quick. */
    private val aurora = observeAuroraForecast()
        .map { forecast ->
            forecast?.let { AuroraMapUi(AuroraOval.overlays(it), it.observedAt, it.forecastFor) }
        }
        .flowOn(defaultDispatcher)

    private val syncStatus = observeSyncStatus()
    private val isOnline = observeConnectivity()

    private val kp = combine(observeKpForecast(), observeCurrentKp()) { forecast, current ->
        KpState(forecast, current)
    }

    val uiState: StateFlow<SpaceWeatherUiState> = combine(
        kp,
        combine(aurora, hemisphere, ::Pair),
        observeSolarActivity(),
        observeAlertSettings(),
        combine(syncStatus, isOnline, ::Pair),
    ) { kp, (aurora, hemisphere), (flare, cme), alerts, (sync, online) ->
        val now = timeProvider.now()
        val upcoming = kp.forecast.filter { it.end.isAfter(now) }
        val gauge = kp.current ?: upcoming.firstOrNull()?.let { CurrentKp(it.start, it.kp) }
        SpaceWeatherUiState(
            currentKp = gauge?.kp,
            currentKpTime = gauge?.time,
            forecast = upcoming,
            aurora = aurora,
            hemisphere = hemisphere,
            flare = flare,
            cme = cme,
            alerts = alerts,
            isRefreshing = sync.isSyncing,
            isOffline = !online,
            lastUpdatedAt = sync.lastSuccessAt,
            lastRefreshFailed = sync.lastAttemptFailed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SpaceWeatherUiState(),
    )

    init {
        requestSync()
        viewModelScope.launch {
            // Back online after a failed refresh: try again rather than waiting for a pull.
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect { requestSync() }
        }
    }

    fun onRefresh() = requestSync(force = true)

    fun onHemisphereSelected(hemisphere: Hemisphere) {
        savedStateHandle[KEY_HEMISPHERE] = hemisphere.name
    }

    fun onAlertsEnabledChange(enabled: Boolean) {
        viewModelScope.launch { setAlertEnabled(enabled) }
    }

    /** Saves the place and, when [enableAlerts] (the user turned alerts on without one), turns them on. */
    fun onAlertLocationChange(latitude: Double, longitude: Double, enableAlerts: Boolean = false) {
        viewModelScope.launch {
            setAlertLocation(latitude, longitude)
            if (enableAlerts) setAlertEnabled(true)
        }
    }

    private data class KpState(val forecast: List<KpReading>, val current: CurrentKp?)

    private companion object {
        const val KEY_HEMISPHERE = "hemisphere"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
