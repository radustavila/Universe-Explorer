package com.radustavila.nasaexplorer.feature.sky

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.sky.IssTrack
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.LocateDeviceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssTrackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePeopleInSpaceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPeopleInSpaceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleIssPassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchCitiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetManualLocationUseCase
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.SkyTonight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sky tonight: everything but the live ISS dot and the crew list is computed on the device from
 * the saved location and the cached orbital elements, so the screen works offline.
 *
 * Opening the screen refreshes the elements and the crew when they are due (CelesTrak twice a day,
 * Launch Library 2 every twelve hours); pull-to-refresh forces it within each source's limits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkyTonightViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    observeSkyTonight: ObserveSkyTonightUseCase,
    observeIssPasses: ObserveIssPassesUseCase,
    observeIssTrack: ObserveIssTrackUseCase,
    observePeople: ObservePeopleInSpaceUseCase,
    observeReminders: ObserveIssPassRemindersEnabledUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val locateDevice: LocateDeviceUseCase,
    private val setManualLocation: SetManualLocationUseCase,
    private val searchCities: SearchCitiesUseCase,
    private val refreshElements: RefreshOrbitalElementsUseCase,
    private val refreshPeople: RefreshPeopleInSpaceUseCase,
    private val setReminders: SetIssPassRemindersEnabledUseCase,
    private val rescheduleReminder: RescheduleIssPassReminderUseCase,
) : ViewModel() {

    private val location = observeLocation()
    private val coordinate = location.map { it?.coordinate }.distinctUntilChanged()
    private val isOnline = observeConnectivity()
    private val cityQuery = MutableStateFlow("")
    private val transient = MutableStateFlow(TransientState())

    private val computed = combine(
        location.map { LoadedLocation(it) }.onStart<LoadedLocation?> { emit(null) },
        coordinate.flatMapLatest { it?.let(observeSkyTonight::invoke) ?: flowOf(null) }.onStart { emit(null) },
        coordinate.flatMapLatest { it?.let(observeIssPasses::invoke) ?: flowOf(null) }.onStart { emit(null) },
        observeIssTrack().onStart<IssTrack?> { emit(null) },
        observePeople(),
    ) { loaded, sky, passes, track, people -> Computed(loaded, sky, passes, track, people) }

    private val citySearch = cityQuery.mapLatest { query ->
        CitySearchUiState(query = query, results = searchCities(query))
    }

    val uiState: StateFlow<SkyTonightUiState> = combine(
        computed,
        observeReminders(),
        isOnline,
        citySearch.onStart { emit(CitySearchUiState()) },
        transient,
    ) { computed, reminders, online, search, transient ->
        SkyTonightUiState(
            isLocationLoaded = computed.location != null,
            location = computed.location?.value,
            sky = computed.sky,
            passes = computed.passes,
            track = computed.track,
            people = computed.people,
            remindersEnabled = reminders,
            isLocating = transient.isLocating,
            isRefreshing = transient.isRefreshing,
            isOffline = !online,
            citySearch = search,
            message = transient.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SkyTonightUiState(),
    )

    init {
        viewModelScope.launch { refresh(force = false, reportFailures = false) }
        viewModelScope.launch {
            // A reminder is computed for a place: re-arm it whenever the place changes.
            coordinate.drop(1).collect { rescheduleReminder() }
        }
        viewModelScope.launch {
            isOnline.distinctUntilChanged().drop(1).filter { it }.collect {
                refresh(force = false, reportFailures = false)
            }
        }
    }

    /** The coarse-location permission was just granted (or already was). */
    fun onLocateDevice() {
        if (transient.value.isLocating) return
        transient.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val message = when (locateDevice()) {
                is DeviceLocationResult.Located -> null
                DeviceLocationResult.PermissionDenied -> SkyMessage.LocationPermissionDenied
                DeviceLocationResult.Unavailable -> SkyMessage.LocationUnavailable
            }
            transient.update { it.copy(isLocating = false, message = message ?: it.message) }
        }
    }

    fun onLocationPermissionDenied() {
        transient.update { it.copy(message = SkyMessage.LocationPermissionDenied) }
    }

    fun onCityQueryChange(query: String) {
        cityQuery.value = query
    }

    fun onCitySelected(city: City) {
        cityQuery.value = ""
        viewModelScope.launch { setManualLocation(city) }
    }

    fun onCoordinatesEntered(coordinate: GeoCoordinate) {
        viewModelScope.launch { setManualLocation(coordinate) }
    }

    fun onRemindersChange(enabled: Boolean) {
        viewModelScope.launch { setReminders(enabled) }
    }

    fun onRefresh() {
        viewModelScope.launch { refresh(force = true, reportFailures = true) }
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    private suspend fun refresh(force: Boolean, reportFailures: Boolean) {
        transient.update { it.copy(isRefreshing = force) }
        val (elementsResult, peopleResult) = coroutineScope {
            val elements = async { refreshElements(force) }
            val people = async { refreshPeople(force) }
            elements.await() to people.await()
        }
        if (elementsResult.succeeded()) rescheduleReminder()
        val failure = listOf(elementsResult, peopleResult)
            .firstNotNullOfOrNull { (it as? SourceRefreshResult.Completed)?.result as? DataResult.Failure }
        transient.update { state ->
            state.copy(
                isRefreshing = false,
                message = if (reportFailures && failure != null) {
                    SkyMessage.RefreshFailed(failure.error.toUiError())
                } else {
                    state.message
                },
            )
        }
    }

    private fun SourceRefreshResult<*>.succeeded(): Boolean =
        this is SourceRefreshResult.Completed && result is DataResult.Success

    /** Distinguishes "location read, none saved" from "not read yet". */
    private data class LoadedLocation(val value: ObserverLocation?)

    private data class Computed(
        val location: LoadedLocation?,
        val sky: SkyTonight?,
        val passes: IssPassForecast?,
        val track: IssTrack?,
        val people: List<PersonInSpace>,
    )

    private data class TransientState(
        val isLocating: Boolean = false,
        val isRefreshing: Boolean = false,
        val message: SkyMessage? = null,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
