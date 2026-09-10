package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveApodFeedUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
) {
    operator fun invoke(): Flow<List<Apod>> = apodRepository.observeFeed()
}
