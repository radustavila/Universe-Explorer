package com.radustavila.nasaexplorer.feature.earthmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Layer and day of the satellite map. Nothing is fetched here: the map streams GIBS tiles itself
 * and MapLibre caches them, so the screen only needs the selection (kept across process death)
 * and connectivity for the offline banner.
 */
@HiltViewModel
class EarthMapViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    timeProvider: TimeProvider,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val maxDate: LocalDate = timeProvider.today().minusDays(1)

    private val mode = savedStateHandle.getStateFlow(KEY_MODE, EarthMapMode.TrueColor.name)
    private val date = savedStateHandle.getStateFlow(KEY_DATE, maxDate.toString())

    val uiState: StateFlow<EarthMapUiState> = combine(mode, date, networkMonitor.isOnline) { mode, date, online ->
        stateOf(mode, date).copy(isOffline = !online)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = stateOf(mode.value, date.value),
    )

    fun onModeSelected(mode: EarthMapMode) {
        savedStateHandle[KEY_MODE] = mode.name
    }

    fun onDateSelected(date: LocalDate) {
        savedStateHandle[KEY_DATE] = date.coerceIn(MIN_DATE, maxDate).toString()
    }

    fun onPreviousDay() = onDateSelected(currentDate().minusDays(1))

    fun onNextDay() = onDateSelected(currentDate().plusDays(1))

    private fun currentDate() = LocalDate.parse(date.value)

    private fun stateOf(mode: String, date: String) = EarthMapUiState(
        mode = EarthMapMode.entries.firstOrNull { it.name == mode } ?: EarthMapMode.TrueColor,
        date = LocalDate.parse(date).coerceIn(MIN_DATE, maxDate),
        minDate = MIN_DATE,
        maxDate = maxDate,
    )

    companion object {
        /** The slider reaches back to 2015; days before Suomi NPP's first image come from Terra. */
        val MIN_DATE: LocalDate = LocalDate.of(2015, 1, 1)

        private const val KEY_MODE = "mode"
        private const val KEY_DATE = "date"
        private const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
