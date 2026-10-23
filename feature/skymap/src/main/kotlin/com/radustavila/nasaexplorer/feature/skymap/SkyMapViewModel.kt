package com.radustavila.nasaexplorer.feature.skymap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.FindConstellationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.LoadSkyCatalogUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.MarkDeepSkySeenUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObservingLogUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyMapBodiesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyMapDeepSkyLayerUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyMapNightModeUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshOrbitalElementsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveDeepSkySeenUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyMapDeepSkyLayerUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyMapNightModeUseCase
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.GalacticCentre
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.ObservingConditions
import com.radustavila.nasaexplorer.core.model.ObservingLogEntry
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyChart
import com.radustavila.nasaexplorer.feature.skymap.render.comet
import com.radustavila.nasaexplorer.feature.skymap.render.deepSkyObject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The sky map: the bundled catalogue turned to the saved location's sky, with the Sun, Moon,
 * planets, the ISS and any comet bright enough to look for, for now or for any time the slider
 * picks. Everything is computed on the device; opening the screen only refreshes the ISS elements
 * when they are due.
 *
 * Where the phone points is not here: the screen reads the rotation sensor and gestures itself, as
 * that changes every frame. Opened from the Milky Way planner, the galactic centre starts selected,
 * which turns the map to it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkyMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeLocation: ObserveObserverLocationUseCase,
    observeNightMode: ObserveSkyMapNightModeUseCase,
    private val loadCatalog: LoadSkyCatalogUseCase,
    private val observeBodies: ObserveSkyMapBodiesUseCase,
    private val findConstellation: FindConstellationUseCase,
    private val setNightMode: SetSkyMapNightModeUseCase,
    private val refreshElements: RefreshOrbitalElementsUseCase,
    observeDeepSkyLayer: ObserveSkyMapDeepSkyLayerUseCase,
    private val setDeepSkyLayer: SetSkyMapDeepSkyLayerUseCase,
    observeLog: ObserveObservingLogUseCase,
    private val markSeen: MarkDeepSkySeenUseCase,
    private val removeSeen: RemoveDeepSkySeenUseCase,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val timeOffset = MutableStateFlow(Duration.ZERO)
    private val selection = MutableStateFlow<SkyTarget?>(
        savedStateHandle.get<String>(DEEP_SKY_ID)?.let(SkyTarget::DeepSky)
            ?: SkyTarget.GalacticCentre.takeIf { savedStateHandle.get<Boolean>(SHOW_GALACTIC_CENTRE) == true },
    )

    private val choices: Flow<Choices> =
        combine(observeNightMode(), observeDeepSkyLayer(), observeLog(), ::Choices)

    private val location = observeLocation().distinctUntilChanged()

    private val chart: Flow<SkyChart> = flow { emit(SkyChart(loadCatalog())) }
        .flowOn(defaultDispatcher)
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** Now plus the slider's offset, every [TICK]: the sky turns a quarter of a degree a minute. */
    private val instants: Flow<Instant> = combine(clock(), timeOffset) { now, offset -> now.plus(offset) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    private val sky: Flow<HorizontalSky?> = combine(chart, location, instants) { chart, location, at ->
        location?.let { chart.at(it.coordinate, at) }
    }.conflate().flowOn(defaultDispatcher)

    private val bodies: Flow<SkyMapBodies?> = location.map { it?.coordinate }.distinctUntilChanged()
        .flatMapLatest { coordinate -> coordinate?.let { observeBodies(it, instants) } ?: flowOf(null) }

    private val scene = combine(
        location.map { LoadedLocation(it) }.onStart<LoadedLocation?> { emit(null) },
        sky.onStart { emit(null) },
        bodies.onStart { emit(null) },
        instants.map<Instant, Instant?> { it }.onStart { emit(null) },
    ) { location, sky, bodies, instant -> Scene(location, sky, bodies, instant) }

    val uiState: StateFlow<SkyMapUiState> = combine(
        scene,
        timeOffset,
        choices,
        selection,
    ) { scene, offset, choices, selected ->
        SkyMapUiState(
            isLocationLoaded = scene.location != null,
            location = scene.location?.value,
            sky = scene.sky,
            bodies = scene.bodies,
            instant = scene.instant,
            timeOffset = offset,
            isNightMode = choices.nightMode,
            selected = selected,
            selectedConstellation = selected?.let { constellationOf(it, scene.sky, scene.bodies) },
            isDeepSkyOn = choices.deepSky,
            observingLog = choices.log,
        )
    }.flowOn(defaultDispatcher).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = SkyMapUiState(),
    )

    init {
        // The ISS needs fresh elements; this is a no-op while the last refresh is recent.
        viewModelScope.launch { refreshElements(force = false) }
    }

    /** Shows the sky [offset] from now; the clock keeps running from there. */
    fun onTimeOffsetChange(offset: Duration) {
        timeOffset.value = offset
    }

    fun onBackToNow() {
        timeOffset.value = Duration.ZERO
    }

    fun onNightModeChange(enabled: Boolean) {
        viewModelScope.launch { setNightMode(enabled) }
    }

    fun onSelect(target: SkyTarget?) {
        selection.value = target
    }

    fun onDeepSkyLayerChange(enabled: Boolean) {
        viewModelScope.launch { setDeepSkyLayer(enabled) }
    }

    /** "Mark as seen" (F23), or an edit of the object's earlier mark. */
    fun onMarkSeen(objectId: String, observedOn: LocalDate, conditions: ObservingConditions?, note: String?) {
        viewModelScope.launch { markSeen(objectId, observedOn, conditions, note) }
    }

    fun onRemoveSeen(objectId: String) {
        viewModelScope.launch { removeSeen(objectId) }
    }

    private fun clock(): Flow<Instant> = flow {
        while (true) {
            emit(timeProvider.now())
            delay(TICK.toMillis())
        }
    }

    private fun constellationOf(target: SkyTarget, sky: HorizontalSky?, bodies: SkyMapBodies?): String? {
        // The deep-sky catalogue says which constellation each object is in.
        if (target is SkyTarget.DeepSky) return sky?.deepSkyObject(target)?.constellation
        val position: EquatorialCoordinate? = when (target) {
            is SkyTarget.StarTarget -> sky?.chart?.catalog?.stars?.getOrNull(target.index)?.position
            is SkyTarget.Body -> bodies?.solarSystem?.firstOrNull { it.body == target.body }?.equatorial
            is SkyTarget.Comet -> bodies?.comet(target.designation)?.equatorial
            SkyTarget.GalacticCentre -> GalacticCentre.POSITION
            is SkyTarget.Constellation, is SkyTarget.Satellite, is SkyTarget.DeepSky -> null
        }
        return position?.let(findConstellation::invoke)
    }

    /** Distinguishes "location read, none saved" from "not read yet". */
    private data class LoadedLocation(val value: ObserverLocation?)

    private data class Choices(
        val nightMode: Boolean,
        val deepSky: Boolean,
        val log: Map<String, ObservingLogEntry>,
    )

    private data class Scene(
        val location: LoadedLocation?,
        val sky: HorizontalSky?,
        val bodies: SkyMapBodies?,
        val instant: Instant?,
    )

    companion object {
        val TICK: Duration = Duration.ofSeconds(1)

        /** [SkyMapRoute.showGalacticCentre]'s key in the saved state. */
        private const val SHOW_GALACTIC_CENTRE = "showGalacticCentre"

        /** [SkyMapRoute.deepSkyId]'s key in the saved state. */
        private const val DEEP_SKY_ID = "deepSkyId"
        private const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
