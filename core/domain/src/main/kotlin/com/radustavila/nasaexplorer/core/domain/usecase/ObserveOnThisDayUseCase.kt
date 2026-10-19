package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * "On this day": the entries published on today's month and day in earlier years, newest first,
 * as far as the cache holds them. [CacheOnThisDayUseCase] fills the cache from the sync worker.
 */
class ObserveOnThisDayUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(): Flow<List<Apod>> =
        apodRepository.observeSameDayInEarlierYears(timeProvider.today())
}
