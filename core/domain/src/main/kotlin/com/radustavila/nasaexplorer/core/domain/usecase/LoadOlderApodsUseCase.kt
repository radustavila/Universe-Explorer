package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Incremental historical loading. The APOD API is date-based, so "the next page" means "the previous
 * date window".
 *
 * Business rules enforced here:
 *  - nothing exists before the first APOD (1995-06-16), so paging stops there;
 *  - paging requires connectivity, and says so instead of failing deep in the stack.
 */
class LoadOlderApodsUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(): DataResult<Int> {
        val oldestRequested = syncMetadataRepository.oldestRequestedDate.first()
        if (oldestRequested != null && !oldestRequested.isAfter(Apod.FIRST_PUBLICATION_DATE)) {
            return DataResult.Success(ARCHIVE_EXHAUSTED)
        }

        if (!networkMonitor.isOnline.first()) {
            return DataResult.Failure(AppError.NoConnection)
        }

        return apodRepository.loadOlderWindow()
    }

    private companion object {
        const val ARCHIVE_EXHAUSTED = 0
    }
}
