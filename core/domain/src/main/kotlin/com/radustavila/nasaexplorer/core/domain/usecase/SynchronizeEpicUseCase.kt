package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The one definition of "synchronise the EPIC cache", run by the EPIC sync worker only.
 *
 * Both collections are refreshed on every run so the natural/enhanced toggle works offline. Each
 * collection is attempted even when the other fails; the first failure is what gets reported, so
 * the worker can still decide whether a retry is worth it. Rotation thumbnails are prefetched for
 * every collection that refreshed, which is what makes the latest day play offline after one sync.
 */
class SynchronizeEpicUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(): DataResult<Int> {
        if (!networkMonitor.isOnline.first()) {
            return DataResult.Failure(AppError.NoConnection)
        }

        val results = EpicCollection.entries.map { collection ->
            epicRepository.refreshLatest(collection).also { result ->
                if (result is DataResult.Success) epicRepository.prefetchLatestThumbnails(collection)
            }
        }

        return results.firstOrNull { it is DataResult.Failure }
            ?: DataResult.Success(results.sumOf { (it as DataResult.Success).data })
    }
}
