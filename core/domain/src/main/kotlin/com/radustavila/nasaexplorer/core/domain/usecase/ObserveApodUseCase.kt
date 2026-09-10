package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveApodUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
) {
    operator fun invoke(date: LocalDate): Flow<Apod?> = apodRepository.observeApod(date)
}
