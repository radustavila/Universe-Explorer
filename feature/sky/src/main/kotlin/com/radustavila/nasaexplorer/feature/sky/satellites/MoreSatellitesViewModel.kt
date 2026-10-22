package com.radustavila.nasaexplorer.feature.sky.satellites

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteForecast
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveConnectivityUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEnabledSatelliteSetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSatellitePassRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSatellitePassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PredictPassGroundTrackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSatelliteSetsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleSatellitePassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSatellitePassReminderEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSatelliteSetEnabledUseCase
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.launch

@Immutable
data class MoreSatellitesUiState(
    val enabledSets: Set<SatelliteSet> = emptySet(),
    /** Null while being computed, or without a saved place. */
    val forecast: SatelliteForecast? = null,
    val reminders: Set<SatelliteSet> = emptySet(),
    /** The pass whose ground track is open, with that track. */
    val selected: SelectedPass? = null,
    val observer: GeoCoordinate? = null,
)

@Immutable
data class SelectedPass(
    val pass: UpcomingPass,
    /** Empty while being computed. */
    val track: List<SatellitePosition> = emptyList(),
)

/**
 * Sky tonight's "More satellites" card, apart from [com.radustavila.nasaexplorer.feature.sky.SkyTonightViewModel]
 * so the ISS's card and state stay as they were: which sets are picked, their merged visible passes,
 * per-set reminders, and the ground track of the pass the user opened.
 *
 * Opening the screen refreshes the picked sets that are due (each at most once a day); pull-to-refresh
 * does not, so CelesTrak's limits hold whatever the user does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoreSatellitesViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    observeEnabledSets: ObserveEnabledSatelliteSetsUseCase,
    observePasses: ObserveSatellitePassesUseCase,
    observeReminders: ObserveSatellitePassRemindersUseCase,
    observeConnectivity: ObserveConnectivityUseCase,
    private val setSetEnabled: SetSatelliteSetEnabledUseCase,
    private val setReminderEnabled: SetSatellitePassReminderEnabledUseCase,
    private val refreshSets: RefreshSatelliteSetsUseCase,
    private val rescheduleReminder: RescheduleSatellitePassReminderUseCase,
    private val predictGroundTrack: PredictPassGroundTrackUseCase,
) : ViewModel() {

    private val coordinate = observeLocation().map { it?.coordinate }.distinctUntilChanged()
    private val selectedPass = MutableStateFlow<UpcomingPass?>(null)

    private val selected = selectedPass.mapLatest { pass ->
        pass?.let { SelectedPass(it, predictGroundTrack(it)) }
    }.onStart { emit(null) }

    val uiState: StateFlow<MoreSatellitesUiState> = combine(
        observeEnabledSets(),
        coordinate.flatMapLatest { observer ->
            observer?.let(observePasses::invoke)?.onStart<SatelliteForecast?> { emit(null) } ?: flowOf(null)
        },
        observeReminders(),
        selected,
        coordinate,
    ) { sets, forecast, reminders, selected, observer ->
        MoreSatellitesUiState(
            enabledSets = sets,
            // A forecast for sets the user has since turned off is not shown while the new one computes.
            forecast = forecast?.takeIf { it.enabledSets == sets - SatelliteSet.ISS },
            reminders = reminders,
            selected = selected?.takeIf { it.pass.satellite.set in sets },
            observer = observer,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = MoreSatellitesUiState(),
    )

    init {
        viewModelScope.launch { refreshSets() }
        viewModelScope.launch {
            // A reminder is computed for a place: re-arm it whenever the place changes.
            coordinate.drop(1).collect { rescheduleReminder() }
        }
        viewModelScope.launch {
            observeConnectivity().distinctUntilChanged().drop(1).filter { it }.collect { refreshSets() }
        }
    }

    fun onSetToggle(set: SatelliteSet, enabled: Boolean) {
        viewModelScope.launch { setSetEnabled(set, enabled) }
    }

    fun onReminderChange(set: SatelliteSet, enabled: Boolean) {
        viewModelScope.launch { setReminderEnabled(set, enabled) }
    }

    /** Opens [pass]'s ground track, or closes it when it is already open. */
    fun onPassClick(pass: UpcomingPass) {
        selectedPass.value = if (selectedPass.value == pass) null else pass
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
