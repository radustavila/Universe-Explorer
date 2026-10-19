package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** The days a date picker may offer: EPIC has gaps (calibration, safe mode), so not every day exists. */
class ObserveEpicAvailableDatesUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
) {
    operator fun invoke(collection: EpicCollection): Flow<List<LocalDate>> =
        epicRepository.observeAvailableDates(collection)
}
