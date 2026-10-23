package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import com.radustavila.nasaexplorer.core.map.gibs.GibsTileMatrixSet
import com.radustavila.nasaexplorer.core.map.gibs.GibsTime
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.EventRecency
import com.radustavila.nasaexplorer.core.model.recency
import java.time.Instant
import java.time.LocalDate

@Immutable
data class EarthEventsUiState(
    /**
     * Open events from the cache, most recently updated first: everything not
     * [expired][EventRecency.Expired], including wildfire [history][EventRecency.History].
     */
    val events: List<EarthEvent> = emptyList(),
    /** Categories the filter switched off. */
    val hiddenCategories: Set<EarthEventCategory> = emptySet(),
    /** Whether wildfires last reported 2 to 6 weeks ago are drawn too. */
    val showWildfireHistory: Boolean = false,
    val selectedEventId: String? = null,
    /** An event's own imagery, after "Show imagery"; `null` shows the plain, cloud-free map. */
    val eventImagery: EventImagery? = null,
    /** The UTC day events' ages are counted to. */
    val today: LocalDate,
    /** False until the cache has been read once, so an empty map is not mistaken for no events. */
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingImagery: Boolean = false,
    val isOffline: Boolean = false,
    val message: EarthEventsMessage? = null,
    /** When the events last synced, for the "Updated …" line over the map. */
    val freshness: DataFreshness = DataFreshness(),
    /** Cached USGS quakes that some filter shows, newest first. */
    val earthquakes: List<Earthquake> = emptyList(),
    /** Which quakes the map shows; null hides them. */
    val earthquakeFilter: EarthquakeFilter? = EarthquakeFilter.DEFAULT,
    val selectedEarthquakeId: String? = null,
    /** The quakes as a list, newest first, instead of the map. */
    val showEarthquakeList: Boolean = false,
    /** A quake the map should move to once (opened from search, the alert or the list). */
    val focusEarthquakeId: String? = null,
    /** What "past week" is counted back from. */
    val now: Instant = Instant.EPOCH,
) {
    /** The quakes the filter picks, newest first. */
    val visibleEarthquakes: List<Earthquake>
        get() = earthquakeFilter?.let { filter -> earthquakes.filter { filter.matches(it, now) } }.orEmpty()

    /** How many quakes each filter would show, for its chip. */
    val earthquakeCounts: List<Pair<EarthquakeFilter, Int>>
        get() = EarthquakeFilter.entries.map { filter -> filter to earthquakes.count { filter.matches(it, now) } }

    /** Shown even when the filter left it out, as when search or the alert opened it. */
    val selectedEarthquake: Earthquake? get() = earthquakes.firstOrNull { it.id == selectedEarthquakeId }

    val focusEarthquake: Earthquake? get() = earthquakes.firstOrNull { it.id == focusEarthquakeId }

    /** The events the map draws (before the category filter): history only while switched on. */
    val shownEvents: List<EarthEvent>
        get() = if (showWildfireHistory) events else events.filter { recencyOf(it).isShownByDefault }

    /** Wildfires the history toggle shows or hides. */
    val wildfireHistoryCount: Int get() = events.count { recencyOf(it) == EventRecency.History }

    /** The categories with shown events and how many, largest first, for the filter. */
    val categoryCounts: List<Pair<EarthEventCategory, Int>>
        get() = countByCategory(shownEvents)

    val visibleEvents: List<EarthEvent> get() = shownEvents.filter { it.category !in hiddenCategories }

    val selectedEvent: EarthEvent? get() = shownEvents.firstOrNull { it.id == selectedEventId }

    fun recencyOf(event: EarthEvent): EventRecency = event.recency(today)

    /**
     * What the map draws under the events: the event's imagery once asked for, otherwise nothing
     * over the map's cloud-free Blue Marble base, so the markers stand out.
     */
    val imagery: GibsImagery?
        get() = eventImagery?.toGibsImagery()
}

/**
 * The imagery "Show imagery" picked for event [eventId]: EONET's suggested [layer] for its category
 * (or true colour when there is none) on [date], the day of the event's latest report.
 */
@Immutable
data class EventImagery(
    val eventId: String,
    val layer: EventImageryLayer?,
    val date: LocalDate,
) {
    fun toGibsImagery(): GibsImagery {
        val gibsLayer = layer?.let { layer ->
            GibsTileMatrixSet.fromId(layer.tileMatrixSet)?.let { matrixSet ->
                GibsLayer(id = layer.id, matrixSet = matrixSet, format = layer.fileExtension, time = GibsTime.Daily())
            }
        } ?: return GibsImagery.trueColor(date)
        return GibsImagery(gibsLayer, date)
    }

    /** `MODIS_Terra_CorrectedReflectance_TrueColor` → `MODIS Terra Corrected Reflectance True Color`. */
    val layerName: String
        get() = toGibsImagery().layer.id
            .replace('_', ' ')
            .replace(CAMEL_CASE_BOUNDARY, " ")
}

private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z])(?=[A-Z])")

/** One-off notices shown in a snackbar. */
enum class EarthEventsMessage {
    /** A refresh the user asked for failed; the cached events are still shown. */
    RefreshFailed,

    /** EONET's layer list could not be loaded; true colour is shown instead. */
    ImageryFallback,
}
