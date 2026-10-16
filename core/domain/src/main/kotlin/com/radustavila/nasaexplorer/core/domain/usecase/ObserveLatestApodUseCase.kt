package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLatestApodUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
) {
    operator fun invoke(): Flow<Apod?> = apodRepository.observeLatestApod()
}
