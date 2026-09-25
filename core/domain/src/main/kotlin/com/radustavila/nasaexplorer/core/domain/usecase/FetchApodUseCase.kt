package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Downloads one specific day on demand, for dates the feed's date-window paging has not reached.
 *
 * Business rules enforced here:
 *  - nothing exists outside `[1995-06-16, today]`, so such a date is rejected without a round trip;
 *  - fetching requires connectivity, and says so instead of failing deep in the stack.
 */
class FetchApodUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val networkMonitor: NetworkMonitor,
) {
    suspend operator fun invoke(date: LocalDate): DataResult<Unit> {
        if (date.isBefore(Apod.FIRST_PUBLICATION_DATE)) {
            return DataResult.Failure(AppError.EmptyResponse)
        }

        if (!networkMonitor.isOnline.first()) {
            return DataResult.Failure(AppError.NoConnection)
        }

        return apodRepository.fetchApod(date)
    }
}
