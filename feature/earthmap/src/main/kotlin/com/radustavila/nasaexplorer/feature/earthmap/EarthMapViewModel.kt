package com.radustavila.nasaexplorer.feature.earthmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEarthquakesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthquakesUseCase
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Layer and day of the satellite map. Nothing is fetched here: the map streams GIBS tiles itself
 * and MapLibre caches them, so the screen only needs the selection (kept across process death)
 * and connectivity for the offline banner. The map's day trails the picked day by
 * [IMAGERY_DEBOUNCE_MILLIS], so tapping through days or dragging the slider does not load every day
 * passed on the way.
 *
 * The quake layer (USGS, magnitude 4.5+ of the past week) comes from the shared cache; turning it on
 * or opening the screen with it on asks for a refresh, which runs at most every 15 minutes.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class EarthMapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    timeProvider: TimeProvider,
    networkMonitor: NetworkMonitor,
    observeEarthquakes: ObserveEarthquakesUseCase,
    private val refreshEarthquakes: RefreshEarthquakesUseCase,
) : ViewModel() {

    private val maxDate: LocalDate = timeProvider.today().minusDays(1)

    private val mode = savedStateHandle.getStateFlow(KEY_MODE, EarthMapMode.TrueColor.name)
    private val date = savedStateHandle.getStateFlow(KEY_DATE, maxDate.toString())
    private val imageryDate = date.debounce(IMAGERY_DEBOUNCE_MILLIS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, date.value)
    private val showQuakes = savedStateHandle.getStateFlow(KEY_QUAKES, false)
    private val selectedQuakeId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED_QUAKE, null)
    private val now = timeProvider.now()
    private val quakes = observeEarthquakes().map { quakes -> quakes.filter { LAYER_FILTER.matches(it, now) } }

    val uiState: StateFlow<EarthMapUiState> = combine(
        combine(mode, date, imageryDate, ::Triple),
        networkMonitor.isOnline,
        quakes,
        showQuakes,
        selectedQuakeId,
    ) { (mode, date, imageryDate), online, quakes, show, selected ->
        stateOf(mode, date, imageryDate).copy(
            isOffline = !online,
            earthquakes = quakes,
            showEarthquakes = show,
            selectedEarthquakeId = selected,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = stateOf(mode.value, date.value, imageryDate.value),
    )

    init {
        if (showQuakes.value) viewModelScope.launch { refreshEarthquakes() }
    }

    fun onEarthquakesToggled() {
        val show = !showQuakes.value
        savedStateHandle[KEY_QUAKES] = show
        if (show) viewModelScope.launch { refreshEarthquakes() } else savedStateHandle[KEY_SELECTED_QUAKE] = null
    }

    fun onEarthquakeSelected(earthquakeId: String) {
        savedStateHandle[KEY_SELECTED_QUAKE] = earthquakeId
    }

    fun onEarthquakeDismissed() {
        savedStateHandle[KEY_SELECTED_QUAKE] = null
    }

    fun onModeSelected(mode: EarthMapMode) {
        savedStateHandle[KEY_MODE] = mode.name
    }

    fun onDateSelected(date: LocalDate) {
        savedStateHandle[KEY_DATE] = date.coerceIn(MIN_DATE, maxDate).toString()
    }

    fun onPreviousDay() = onDateSelected(currentDate().minusDays(1))

    fun onNextDay() = onDateSelected(currentDate().plusDays(1))

    private fun currentDate() = LocalDate.parse(date.value)

    private fun stateOf(mode: String, date: String, imageryDate: String) = EarthMapUiState(
        mode = EarthMapMode.entries.firstOrNull { it.name == mode } ?: EarthMapMode.TrueColor,
        date = LocalDate.parse(date).coerceIn(MIN_DATE, maxDate),
        imageryDate = LocalDate.parse(imageryDate).coerceIn(MIN_DATE, maxDate),
        minDate = MIN_DATE,
        maxDate = maxDate,
    )

    companion object {
        /** The slider reaches back to 2015; days before Suomi NPP's first image come from Terra. */
        val MIN_DATE: LocalDate = LocalDate.of(2015, 1, 1)

        private const val KEY_MODE = "mode"
        private const val KEY_DATE = "date"
        private const val KEY_QUAKES = "show_earthquakes"
        private const val KEY_SELECTED_QUAKE = "selected_earthquake"

        /** The satellite map is about the imagery: only the quakes felt widely. */
        private val LAYER_FILTER = EarthquakeFilter.MAGNITUDE_4_5
        private const val STATE_TIMEOUT_MILLIS = 5_000L

        /** How long the picked day has to stay put before the map loads it. */
        const val IMAGERY_DEBOUNCE_MILLIS = 400L
    }
}
