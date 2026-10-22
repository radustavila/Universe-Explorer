package com.radustavila.nasaexplorer.feature.earthevents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.GetEventImageryLayerUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveOpenEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthEventsUseCase
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
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
 * one. Filter, selection and shown imagery survive rotation and process death.
 */
@HiltViewModel
class EarthEventsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeOpenEvents: ObserveOpenEarthEventsUseCase,
    private val refreshEarthEvents: RefreshEarthEventsUseCase,
    private val getEventImageryLayer: GetEventImageryLayerUseCase,
    timeProvider: TimeProvider,
    observeDataFreshness: ObserveDataFreshnessUseCase,
) : ViewModel() {

    private val latestImageryDate: LocalDate = timeProvider.today().minusDays(1)

    private val hidden = savedStateHandle.getStateFlow(KEY_HIDDEN, emptyList<String>())
    private val selectedId = savedStateHandle.getStateFlow<String?>(KEY_SELECTED, null)
    private val imagery = savedStateHandle.getStateFlow<List<String>?>(KEY_IMAGERY, null)
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<EarthEventsUiState> = combine(
        observeOpenEvents(),
        combine(hidden, selectedId, imagery, ::Selection),
        transient,
        observeDataFreshness(SyncSource.EONET),
    ) { events, selection, transient, freshness ->
        EarthEventsUiState(
            events = events,
            hiddenCategories = selection.hidden.mapNotNull(EarthEventCategory::fromKey).toSet(),
            selectedEventId = selection.selectedId?.takeIf { id -> events.any { it.id == id } },
            eventImagery = selection.imagery?.let(::decodeImagery),
            latestImageryDate = latestImageryDate,
            isLoaded = true,
            isRefreshing = transient.isRefreshing,
            isLoadingImagery = transient.isLoadingImagery,
            isOffline = freshness.isOffline,
            message = transient.message,
            freshness = freshness,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = EarthEventsUiState(latestImageryDate = latestImageryDate),
    )

    init {
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    fun onCategoryToggled(category: EarthEventCategory) {
        val current = hidden.value
        savedStateHandle[KEY_HIDDEN] = ArrayList(if (category.key in current) current - category.key else current + category.key)
    }

    fun onEventSelected(eventId: String) {
        savedStateHandle[KEY_SELECTED] = eventId
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

    /** Back to the latest true colour. */
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
        minOf(event.lastUpdate.atOffset(ZoneOffset.UTC).toLocalDate(), latestImageryDate)

    private data class Selection(val hidden: List<String>, val selectedId: String?, val imagery: List<String>?)

    private data class Transient(
        val isRefreshing: Boolean = false,
        val isLoadingImagery: Boolean = false,
        val message: EarthEventsMessage? = null,
    )

    companion object {
        private const val KEY_HIDDEN = "hidden_categories"
        private const val KEY_SELECTED = "selected_event"
        private const val KEY_IMAGERY = "event_imagery"
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
