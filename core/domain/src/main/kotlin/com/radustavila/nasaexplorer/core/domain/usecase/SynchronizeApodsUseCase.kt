package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The one place where "synchronise the cache with NASA" is defined.
 *
 * Invoked by the background worker and by manual refresh, never by a Composable or a ViewModel
 * directly. It short-circuits when offline (so the UI gets an immediate, meaningful error instead of
 * a socket timeout) and records the timestamp of the last successful run.
 */
class SynchronizeApodsUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): DataResult<Int> {
        if (!networkMonitor.isOnline.first()) {
            return DataResult.Failure(AppError.NoConnection)
        }

        return when (val result = apodRepository.refreshRecent()) {
            is DataResult.Success -> {
                syncMetadataRepository.setLastSuccessfulSync(timeProvider.now())
                result
            }

            is DataResult.Failure -> result
        }
    }
}
