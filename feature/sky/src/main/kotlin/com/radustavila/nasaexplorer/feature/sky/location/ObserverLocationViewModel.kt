package com.radustavila.nasaexplorer.feature.sky.location

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.usecase.ClearObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.LocateDeviceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleIssPassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SearchCitiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetManualLocationUseCase
import com.radustavila.nasaexplorer.core.model.City
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.feature.sky.CitySearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ObserverLocationUiState(
    /** False until the saved location has been read. */
    val isLoaded: Boolean = false,
    val location: ObserverLocation? = null,
    val isLocating: Boolean = false,
    val citySearch: CitySearchUiState = CitySearchUiState(),
    val message: ObserverLocationMessage? = null,
)

enum class ObserverLocationMessage {
    /** Location access was refused; a city can still be picked. */
    PERMISSION_DENIED,

    /** Location is off, or no fix arrived. */
    UNAVAILABLE,
}

/**
 * The saved observer location outside Sky tonight (Settings): the same city picker and coarse
 * device fix, plus forgetting the place. Every change re-arms the ISS reminder, which Sky tonight
 * would otherwise only do while it is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ObserverLocationViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    private val locateDevice: LocateDeviceUseCase,
    private val setManualLocation: SetManualLocationUseCase,
    private val clearLocation: ClearObserverLocationUseCase,
    private val searchCities: SearchCitiesUseCase,
    private val rescheduleReminder: RescheduleIssPassReminderUseCase,
) : ViewModel() {

    private val cityQuery = MutableStateFlow("")
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<ObserverLocationUiState> = combine(
        observeLocation().map { Loaded(it) }.onStart<Loaded?> { emit(null) },
        cityQuery.mapLatest { query -> CitySearchUiState(query, searchCities(query)) }.onStart { emit(CitySearchUiState()) },
        transient,
    ) { loaded, search, transient ->
        ObserverLocationUiState(
            isLoaded = loaded != null,
            location = loaded?.location,
            isLocating = transient.isLocating,
            citySearch = search,
            message = transient.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS), ObserverLocationUiState())

    /** The coarse-location permission was just granted (or already was). */
    fun onLocateDevice() {
        if (transient.value.isLocating) return
        transient.update { it.copy(isLocating = true, message = null) }
        viewModelScope.launch {
            val message = when (locateDevice()) {
                is DeviceLocationResult.Located -> {
                    rescheduleReminder()
                    null
                }

                DeviceLocationResult.PermissionDenied -> ObserverLocationMessage.PERMISSION_DENIED
                DeviceLocationResult.Unavailable -> ObserverLocationMessage.UNAVAILABLE
            }
            transient.update { it.copy(isLocating = false, message = message) }
        }
    }

    fun onLocationPermissionDenied() {
        transient.update { it.copy(message = ObserverLocationMessage.PERMISSION_DENIED) }
    }

    fun onCityQueryChange(query: String) {
        cityQuery.value = query
    }

    fun onCitySelected(city: City) {
        cityQuery.value = ""
        save { setManualLocation(city) }
    }

    fun onCoordinatesEntered(coordinate: GeoCoordinate) {
        save { setManualLocation(coordinate) }
    }

    fun onClear() {
        transient.update { it.copy(message = null) }
        viewModelScope.launch { clearLocation() }
    }

    private fun save(block: suspend () -> Unit) {
        transient.update { it.copy(message = null) }
        viewModelScope.launch {
            block()
            rescheduleReminder()
        }
    }

    private data class Loaded(val location: ObserverLocation?)

    private data class Transient(val isLocating: Boolean = false, val message: ObserverLocationMessage? = null)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
