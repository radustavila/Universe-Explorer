package com.radustavila.nasaexplorer.feature.earthevents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.GetEventImageryLayerUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEarthquakesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOpenEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthquakesUseCase
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.EventRecency
import com.radustavila.nasaexplorer.core.model.SyncSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The live events map. Events come from the cache only; opening the screen asks for a refresh,
 * which [RefreshEarthEventsUseCase] skips while the cache is fresh, and the refresh button forces
 * one. USGS quakes ride along: refreshed on open and with the button, at most every 15 minutes
 * ([RefreshEarthquakesUseCase]). Filters, wildfire history toggle, selection, the quake list and
 * shown imagery survive rotation and process death. Opened with an `earthquake` argument (search,
 * the strong-quake alert), the map starts on that quake.
 */
// One handler per control on the screen.
@Suppress("TooManyFunctions")
@HiltViewModel
class EarthEventsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeOpenEvents: ObserveOpenEarthEventsUseCase,
    private val refreshEarthEvents: RefreshEarthEventsUseCase,
    private val getEventImageryLayer: GetEventImageryLayerUseCase,
    timeProvider: TimeProvider,
    observeDataFreshness: ObserveDataFreshnessUseCase,
    observeEarthquakes: ObserveEarthquakesUseCase,
    private val refreshEarthquakes: RefreshEarthquakesUseCase,
) : ViewModel() {

    private val today: LocalDate = timeProvider.today()
    private val now = timeProvider.now()

    private val hidden = savedStateHandle.getStateFlow(KEY_HIDDEN, emptyList<String>())
    private val showHistory = savedStateHandle.getStateFlow(KEY_HISTORY, false)
    private val selectedId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED, null)
    private val imagery = savedStateHandle.getStateFlow<List<String>?>(KEY_IMAGERY, null)
    private val quakeFilter = savedStateHandle.getStateFlow(KEY_QUAKE_FILTER, EarthquakeFilter.DEFAULT.key)
    private val selectedQuakeId = savedStateHandle.getStateFlow<String?>(KEY_QUAKE_SELECTED, null)
    private val showQuakeList = savedStateHandle.getStateFlow(KEY_QUAKE_LIST, false)
    private val transient = MutableStateFlow(Transient())

    private val quakes = combine(
        observeEarthquakes(),
        quakeFilter,
        selectedQuakeId,
        showQuakeList,
    ) { quakes, filter, selected, list ->
        val shown = selected?.takeIf { id -> quakes.any { it.id == id } }
        QuakeSelection(quakes, EarthquakeFilter.fromKey(filter), shown, list)
    }

    val uiState: StateFlow<EarthEventsUiState> = combine(
        observeOpenEvents(includeHistory = true),
        combine(hidden, showHistory, selectedId, imagery, ::Selection),
        transient,
        observeDataFreshness(SyncSource.EONET),
        quakes,
    ) { events, selection, transient, freshness, quakes ->
        EarthEventsUiState(
            events = events,
            hiddenCategories = selection.hidden.mapNotNull(EarthEventCategory::fromKey).toSet(),
            showWildfireHistory = selection.showHistory,
            selectedEventId = selection.selectedId?.takeIf { id -> events.any { it.id == id } },
            eventImagery = selection.imagery?.let(::decodeImagery),
            today = today,
            isLoaded = true,
            isRefreshing = transient.isRefreshing,
            isLoadingImagery = transient.isLoadingImagery,
            isOffline = freshness.isOffline,
            message = transient.message,
            freshness = freshness,
            earthquakes = quakes.quakes,
            earthquakeFilter = quakes.filter,
            selectedEarthquakeId = quakes.selectedId,
            showEarthquakeList = quakes.showList,
            focusEarthquakeId = transient.focusEarthquakeId?.takeIf { id -> quakes.quakes.any { it.id == id } },
            now = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = EarthEventsUiState(today = today, showWildfireHistory = showHistory.value, now = now),
    )

    init {
        savedStateHandle.remove<String>(ARG_EARTHQUAKE)?.let { id ->
            savedStateHandle[KEY_QUAKE_SELECTED] = id
            savedStateHandle[KEY_SELECTED] = null
            savedStateHandle[KEY_QUAKE_LIST] = false
            transient.update { it.copy(focusEarthquakeId = id) }
        }
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    fun onCategoryToggled(category: EarthEventCategory) {
        val current = hidden.value
        savedStateHandle[KEY_HIDDEN] = ArrayList(if (category.key in current) current - category.key else current + category.key)
    }

    /** Shows or hides wildfires last reported 2 to 6 weeks ago; hiding one closes its sheet. */
    fun onWildfireHistoryToggled() {
        val show = !showHistory.value
        savedStateHandle[KEY_HISTORY] = show
        val selected = uiState.value.run { events.firstOrNull { it.id == selectedEventId }?.let(::recencyOf) }
        if (!show && selected == EventRecency.History) savedStateHandle[KEY_SELECTED] = null
    }

    fun onEventSelected(eventId: String) {
        savedStateHandle[KEY_SELECTED] = eventId
        savedStateHandle[KEY_QUAKE_SELECTED] = null
    }

    /** One filter at a time; picking the shown one again hides the quakes. */
    fun onEarthquakeFilterSelected(filter: EarthquakeFilter) {
        val shown = EarthquakeFilter.fromKey(quakeFilter.value)
        savedStateHandle[KEY_QUAKE_FILTER] = if (filter == shown) FILTER_OFF else filter.key
        if (filter == shown) savedStateHandle[KEY_QUAKE_LIST] = false
    }

    fun onEarthquakeSelected(earthquakeId: String) {
        savedStateHandle[KEY_QUAKE_SELECTED] = earthquakeId
        savedStateHandle[KEY_SELECTED] = null
    }

    /** A quake picked in the list: back to the map, which flies to it. */
    fun onEarthquakeListItemSelected(earthquakeId: String) {
        onEarthquakeSelected(earthquakeId)
        savedStateHandle[KEY_QUAKE_LIST] = false
        transient.update { it.copy(focusEarthquakeId = earthquakeId) }
    }

    fun onEarthquakeDismissed() {
        savedStateHandle[KEY_QUAKE_SELECTED] = null
    }

    fun onEarthquakeListToggled() {
        savedStateHandle[KEY_QUAKE_LIST] = !showQuakeList.value
    }

    /** The map moved to the quake it was asked to show. */
    fun onEarthquakeFocused() {
        transient.update { it.copy(focusEarthquakeId = null) }
    }

    fun onEventDismissed() {
        savedStateHandle[KEY_SELECTED] = null
    }

    /** Switches the map to the selected event's day and a layer EONET suggests for its category. */
    fun onShowImagery() {
        val event = uiState.value.selectedEvent ?: return
        if (transient.value.isLoadingImagery) return
        transient.update { it.copy(isLoadingImagery = true) }
        viewModelScope.launch {
            val result = getEventImageryLayer(event.category)
            val layer = (result as? DataResult.Success)?.data
            savedStateHandle[KEY_IMAGERY] = encodeImagery(EventImagery(event.id, layer, imageryDateOf(event)))
            transient.update {
                it.copy(
                    isLoadingImagery = false,
                    message = if (result is DataResult.Failure) EarthEventsMessage.ImageryFallback else it.message,
                )
            }
        }
    }

    /** Back to the plain map. */
    fun onHideImagery() {
        savedStateHandle[KEY_IMAGERY] = null
    }

    fun onMessageShown() {
        transient.update { it.copy(message = null) }
    }

    private fun refresh(force: Boolean) {
        if (transient.value.isRefreshing) return
        transient.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            // Never forced: USGS updates every minute, a revalidation every 15 is plenty.
            launch { refreshEarthquakes() }
            val outcome = refreshEarthEvents(force = force)
            val failed = outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Failure
            transient.update {
                it.copy(
                    isRefreshing = false,
                    // Opening the screen offline is normal and the banner says so; only a refresh
                    // the user asked for deserves a notice.
                    message = if (failed && force) EarthEventsMessage.RefreshFailed else it.message,
                )
            }
        }
    }

    /**
     * The UTC day of the event's latest report, but no later than yesterday: today's global mosaic
     * is still being assembled and mostly empty.
     */
    private fun imageryDateOf(event: EarthEvent): LocalDate =
        minOf(event.lastUpdate.atOffset(ZoneOffset.UTC).toLocalDate(), today.minusDays(1))

    private data class Selection(
        val hidden: List<String>,
        val showHistory: Boolean,
        val selectedId: String?,
        val imagery: List<String>?,
    )

    private data class QuakeSelection(
        val quakes: List<Earthquake>,
        val filter: EarthquakeFilter?,
        val selectedId: String?,
        val showList: Boolean,
    )

    private data class Transient(
        val isRefreshing: Boolean = false,
        val isLoadingImagery: Boolean = false,
        val message: EarthEventsMessage? = null,
        val focusEarthquakeId: String? = null,
    )

    companion object {
        private const val KEY_HIDDEN = "hidden_categories"
        private const val KEY_HISTORY = "show_wildfire_history"
        private const val KEY_SELECTED = "selected_event"
        private const val KEY_IMAGERY = "event_imagery"
        private const val KEY_QUAKE_FILTER = "earthquake_filter"
        private const val KEY_QUAKE_SELECTED = "selected_earthquake"
        private const val KEY_QUAKE_LIST = "earthquake_list"
        private const val FILTER_OFF = "off"

        /** [EarthEventsRoute.earthquake]: read once, then the selection lives on its own. */
        internal const val ARG_EARTHQUAKE = "earthquake"
        private const val STATE_TIMEOUT_MILLIS = 5_000L

        /** `[eventId, date, layerId, matrixSet, extension]`, the last three empty without a layer. */
        private fun encodeImagery(imagery: EventImagery): ArrayList<String> = arrayListOf(
            imagery.eventId,
            imagery.date.toString(),
            imagery.layer?.id.orEmpty(),
            imagery.layer?.tileMatrixSet.orEmpty(),
            imagery.layer?.fileExtension.orEmpty(),
        )

        private fun decodeImagery(saved: List<String>): EventImagery? {
            if (saved.size != IMAGERY_FIELDS) return null
            val (eventId, date, layerId, matrixSet, extension) = saved
            val layer = if (layerId.isEmpty()) null else EventImageryLayer(layerId, matrixSet, extension)
            return EventImagery(eventId = eventId, layer = layer, date = LocalDate.parse(date))
        }

        private const val IMAGERY_FIELDS = 5
    }
}
