package com.radustavila.nasaexplorer.feature.spaceweather

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.CanAutoPlaySunVideoUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.GetSunImageAtUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSolarActivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSpaceWeatherSyncStatusUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSunUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSunImageUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SelectSunChannelUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.feature.spaceweather.components.AuroraOval
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Kp, its forecast, the aurora map, the Sun now (SDO's newest picture of the chosen channel), the
 * Sun's latest flare and CME, and the alert settings, all from the local cache. Opening the screen
 * asks the space weather job for a refresh including the aurora grid (the job skips what is fresh);
 * the 30-minute background refresh runs only while aurora alerts are on. The Sun's picture is
 * refreshed here, per channel, as the user picks one; its 48-hour video is only composed once the
 * network is known to be unmetered or the user tapped play.
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
    private val observeSun: ObserveSunUseCase,
    private val selectSunChannel: SelectSunChannelUseCase,
    private val refreshSunImage: RefreshSunImageUseCase,
    private val canAutoPlaySunVideo: CanAutoPlaySunVideoUseCase,
    private val getSunImageAt: GetSunImageAtUseCase,
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

    private val sunRefresh = MutableStateFlow(SunRefresh())
    private val sunVideo = MutableStateFlow(SunVideo.Closed)
    private var sunVideoCheck: Job? = null

    private val sun = combine(observeSun(), sunRefresh, sunVideo) { now, refresh, video ->
        SunUiState(
            channel = now.channel,
            image = now.image,
            videoUrl = now.videoUrl,
            isRefreshing = refresh.isRefreshing,
            lastRefreshFailed = refresh.lastFailed,
            video = video,
        )
    }

    private val solar = combine(observeSolarActivity(), sun) { (flare, cme), sun -> SolarState(flare, cme, sun) }

    private val kp = combine(observeKpForecast(), observeCurrentKp()) { forecast, current ->
        KpState(forecast, current)
    }

    val uiState: StateFlow<SpaceWeatherUiState> = combine(
        kp,
        combine(aurora, hemisphere, ::Pair),
        solar,
        observeAlertSettings(),
        combine(syncStatus, isOnline, ::Pair),
    ) { kp, (aurora, hemisphere), solar, alerts, (sync, online) ->
        val now = timeProvider.now()
        val upcoming = kp.forecast.filter { it.end.isAfter(now) }
        val gauge = kp.current ?: upcoming.firstOrNull()?.let { CurrentKp(it.start, it.kp) }
        SpaceWeatherUiState(
            currentKp = gauge?.kp,
            currentKpTime = gauge?.time,
            forecast = upcoming,
            aurora = aurora,
            hemisphere = hemisphere,
            flare = solar.flare,
            cme = solar.cme,
            flareImageUrl = solar.flare?.let { getSunImageAt(it.peakTime ?: it.beginTime) },
            sun = solar.sun,
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
            // The picture of whichever channel is chosen, now and after each switch.
            observeSun().map { it.channel }.distinctUntilChanged().collectLatest { refreshSun(it, force = false) }
        }
        viewModelScope.launch {
            // Back online after a failed refresh: try again rather than waiting for a pull.
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect {
                requestSync()
                refreshSun(observeSun.selectedChannel(), force = false)
            }
        }
    }

    fun onRefresh() {
        requestSync(force = true)
        viewModelScope.launch { refreshSun(observeSun.selectedChannel(), force = true) }
    }

    fun onSunChannelSelected(channel: SunChannel) {
        viewModelScope.launch {
            selectSunChannel(channel)
            // Another channel is another download: on mobile data, ask again.
            if (sunVideo.value != SunVideo.Closed) checkSunVideo()
        }
    }

    /** The full-screen view switched to the 48-hour video. */
    fun onSunVideoSelected() = checkSunVideo()

    /** The user tapped play on mobile data. */
    fun onSunVideoConfirmed() {
        sunVideoCheck?.cancel()
        sunVideo.value = SunVideo.Playing
    }

    /** The video was switched away from or the full-screen view closed. */
    fun onSunVideoClosed() {
        sunVideoCheck?.cancel()
        sunVideo.value = SunVideo.Closed
    }

    private fun checkSunVideo() {
        sunVideoCheck?.cancel()
        sunVideo.value = SunVideo.Checking
        sunVideoCheck = viewModelScope.launch {
            sunVideo.value = if (canAutoPlaySunVideo()) SunVideo.Playing else SunVideo.AskFirst
        }
    }

    private suspend fun refreshSun(channel: SunChannel, force: Boolean) {
        sunRefresh.update { it.copy(isRefreshing = true) }
        val outcome = refreshSunImage(channel, force)
        val failed = outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Failure
        sunRefresh.value = SunRefresh(isRefreshing = false, lastFailed = failed)
    }

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

    private data class SunRefresh(val isRefreshing: Boolean = false, val lastFailed: Boolean = false)

    private data class SolarState(val flare: SolarFlare?, val cme: CoronalMassEjection?, val sun: SunUiState)

    private data class KpState(val forecast: List<KpReading>, val current: CurrentKp?)

    private companion object {
        const val KEY_HEMISPHERE = "hemisphere"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
