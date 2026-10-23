package com.radustavila.nasaexplorer.feature.skymap.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.LoadSkyCatalogUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.MarkDeepSkySeenUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDeepSkyTonightUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObservingLogUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveDeepSkySeenUseCase
import com.radustavila.nasaexplorer.core.model.DeepSkyCatalogue
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The observing log (F23) and Sky tonight's "Tonight's best" card: the Messier and Caldwell
 * checklists from the bundled catalogue, the user's marks, and what climbs 30° in tonight's dark
 * hours at the saved place. Everything is on the device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ObservingLogViewModel @Inject constructor(
    observeLocation: ObserveObserverLocationUseCase,
    private val loadCatalog: LoadSkyCatalogUseCase,
    observeLog: ObserveObservingLogUseCase,
    private val observeTonight: ObserveDeepSkyTonightUseCase,
    private val markSeen: MarkDeepSkySeenUseCase,
    private val removeSeen: RemoveDeepSkySeenUseCase,
) : ViewModel() {

    private val filters = MutableStateFlow(Filters())

    private val catalog: Flow<SkyCatalog> = flow { emit(loadCatalog()) }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    private val tonight: Flow<TonightSky> = combine(
        catalog.map { it.deepSky.filter { item -> item.messier != null || item.caldwell != null } },
        observeLocation().map { it?.coordinate }.distinctUntilChanged(),
    ) { objects, place -> objects to place }
        .flatMapLatest { (objects, place) ->
            if (place == null) {
                flowOf(TonightSky.NoPlace)
            } else {
                observeTonight(place, objects).map { it?.let(TonightSky::Known) ?: TonightSky.NotDark }
            }
        }

    val uiState: StateFlow<ObservingLogUiState> = combine(
        catalog.map<SkyCatalog, SkyCatalog?> { it }.onStart { emit(null) },
        observeLog(),
        tonight.onStart { emit(TonightSky.Loading) },
        filters,
    ) { catalog, log, tonight, filters ->
        ObservingLogUiState(
            objects = catalog?.deepSky?.filter { it.messier != null || it.caldwell != null },
            constellationNames = catalog?.constellationNames().orEmpty(),
            log = log,
            tonight = tonight,
            catalogue = filters.catalogue,
            notSeenOnly = filters.notSeenOnly,
            upTonightOnly = filters.upTonightOnly,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = ObservingLogUiState(),
    )

    fun onCatalogueChange(catalogue: DeepSkyCatalogue) = filters.update { it.copy(catalogue = catalogue) }

    fun onNotSeenOnlyChange(enabled: Boolean) = filters.update { it.copy(notSeenOnly = enabled) }

    fun onUpTonightOnlyChange(enabled: Boolean) = filters.update { it.copy(upTonightOnly = enabled) }

    fun onMarkSeen(objectId: String, observedOn: LocalDate, conditions: ObservingConditions?, note: String?) {
        viewModelScope.launch { markSeen(objectId, observedOn, conditions, note) }
    }

    fun onRemoveSeen(objectId: String) {
        viewModelScope.launch { removeSeen(objectId) }
    }

    private data class Filters(
        val catalogue: DeepSkyCatalogue = DeepSkyCatalogue.MESSIER,
        val notSeenOnly: Boolean = false,
        val upTonightOnly: Boolean = false,
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
