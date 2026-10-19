package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRefreshOutcome
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Refreshes the upcoming-launches cache as part of the regular sync.
 *
 * Runs from the sync worker alongside [SynchronizeApodsUseCase], never from the UI. It is
 * best-effort: the repository throttles it to one Launch Library 2 call per refresh interval, and
 * its failures never decide whether the APOD sync is retried, since a retry would only spend more of
 * the small hourly budget.
 */
class SynchronizeUpcomingLaunchesUseCase @Inject constructor(
    private val launchRepository: LaunchRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(): DataResult<LaunchRefreshOutcome> {
        if (!networkMonitor.isOnline.first()) {
            return DataResult.Failure(AppError.NoConnection)
        }
        return launchRepository.refreshUpcomingLaunches()
    }
}
