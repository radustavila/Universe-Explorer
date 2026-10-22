package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.EventRecency
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.model.recency
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Open natural events from the local cache, without the ones gone quiet: a wildfire with no report
 * for six weeks is [EventRecency.Expired] even while EONET keeps it open. Wildfires last reported 2
 * to 6 weeks ago ([EventRecency.History]) are only included with [includeHistory].
 */
class ObserveOpenEarthEventsUseCase @Inject constructor(
    private val repository: EarthEventRepository,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(includeHistory: Boolean = false): Flow<List<EarthEvent>> =
        repository.observeOpenEvents().map { events ->
            val today = timeProvider.today()
            events.filter { event ->
                val recency = event.recency(today)
                recency.isShownByDefault || (includeHistory && recency == EventRecency.History)
            }
        }
}

/**
 * Refreshes the events cache at most once per [MIN_REFRESH_INTERVAL], unless [force]d by the user.
 * Runs from the periodic events sync and when the events screen opens (skipped while fresh).
 */
class RefreshEarthEventsUseCase @Inject constructor(
    private val repository: EarthEventRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> =
        refreshSource(SyncSource.EONET, minInterval = MIN_REFRESH_INTERVAL, force = force) {
            repository.refreshOpenEvents()
        }

    companion object {
        /** EONET's feeds update a few times a day; storms every six hours. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(3)
    }
}

/** The satellite imagery layer that shows an event of [category] best (see the repository). */
class GetEventImageryLayerUseCase @Inject constructor(
    private val repository: EarthEventRepository,
) {
    suspend operator fun invoke(category: EarthEventCategory): DataResult<EventImageryLayer?> =
        repository.imageryLayerFor(category)
}
