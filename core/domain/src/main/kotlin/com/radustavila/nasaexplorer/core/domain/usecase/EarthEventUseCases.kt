package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Open natural events, straight from the local cache. */
class ObserveOpenEarthEventsUseCase @Inject constructor(
    private val repository: EarthEventRepository,
) {
    operator fun invoke(): Flow<List<EarthEvent>> = repository.observeOpenEvents()
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
