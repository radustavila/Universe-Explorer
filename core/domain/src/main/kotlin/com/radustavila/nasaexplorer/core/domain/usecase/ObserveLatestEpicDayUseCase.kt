package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveLatestEpicDayUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
) {
    operator fun invoke(collection: EpicCollection = EpicCollection.NATURAL): Flow<List<EpicImage>> =
        epicRepository.observeLatestDay(collection)
}
