package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.succeeded
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * The one definition of "synchronise the EPIC cache", run by the EPIC sync worker only.
 *
 * Goes through [RefreshSourceUseCase] as [SyncSource.EPIC]: the outcome is recorded for the Debug
 * screen, and a sync requested again within [REFRESH_INTERVAL] of a success is skipped. EPIC
 * publishes about once a day, so opening Today and the Earth tab need not download it every time.
 *
 * Both collections are refreshed on every run, concurrently, so the natural/enhanced toggle works
 * offline. Each collection is attempted even when the other fails; the first failure is what gets reported, so
 * the worker can still decide whether a retry is worth it. Rotation thumbnails are prefetched for
 * every collection that refreshed, which is what makes the latest day play offline after one sync.
 * A successful run re-renders the Earth today widget.
 */
class SynchronizeEpicUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
    private val networkMonitor: NetworkMonitor,
    private val refreshSource: RefreshSourceUseCase,
    private val widgetUpdater: HomeWidgetUpdater,
) {
    suspend operator fun invoke(): SourceRefreshResult<Int> {
        // Not worth recording as a failure of the source: nothing was even attempted.
        if (!networkMonitor.isOnline.first()) {
            return SourceRefreshResult.Completed(DataResult.Failure(AppError.NoConnection))
        }

        return refreshSource(SyncSource.EPIC, minInterval = REFRESH_INTERVAL) { refreshAllCollections() }
            .also { outcome -> if (outcome.succeeded()) widgetUpdater.update(HomeWidget.EARTH_TODAY) }
    }

    private suspend fun refreshAllCollections(): DataResult<Int> {
        // The collections are independent: refreshed (and prefetched) side by side.
        val results = coroutineScope {
            EpicCollection.entries.map { collection ->
                async {
                    epicRepository.refreshLatest(collection).also { result ->
                        if (result is DataResult.Success) epicRepository.prefetchLatestThumbnails(collection)
                    }
                }
            }.awaitAll()
        }

        return results.firstOrNull { it is DataResult.Failure }
            ?: DataResult.Success(results.sumOf { (it as DataResult.Success).data })
    }

    companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofHours(3)
    }
}
