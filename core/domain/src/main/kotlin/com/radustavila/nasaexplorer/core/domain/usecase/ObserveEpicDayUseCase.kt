package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveEpicDayUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
) {
    operator fun invoke(collection: EpicCollection, date: LocalDate): Flow<List<EpicImage>> =
        epicRepository.observeDay(collection, date)
}
