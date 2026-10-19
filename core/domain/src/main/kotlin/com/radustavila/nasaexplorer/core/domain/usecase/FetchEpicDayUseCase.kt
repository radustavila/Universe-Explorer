package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.model.EpicCollection
import java.time.LocalDate
import javax.inject.Inject

/**
 * Downloads one archive day the sync does not keep. Like [FetchApodUseCase], the result lands in
 * the cache and reaches the screen through the day's Flow; the return value only reports failure.
 */
class FetchEpicDayUseCase @Inject constructor(
    private val epicRepository: EpicRepository,
) {
    suspend operator fun invoke(collection: EpicCollection, date: LocalDate): DataResult<Int> =
        epicRepository.fetchDay(collection, date)
}
