package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import java.time.MonthDay
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Downloads today's month and day from every earlier year of the archive that the cache does not
 * hold yet, one single-day request each, so "On this day" works offline afterwards.
 *
 * Runs from the sync worker after a successful sync, never from the UI. Days already cached are not
 * requested again, so after the first run of a day this costs nothing. A day NASA did not publish
 * (the archive has gaps) is skipped; a connectivity-type failure stops the run, since every
 * remaining request would fail the same way - the next sync picks up where this one stopped.
 *
 * @return the number of days downloaded.
 */
class CacheOnThisDayUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): DataResult<Int> {
        val today = timeProvider.today()
        val cached = apodRepository.observeSameDayInEarlierYears(today).first()
            .mapTo(mutableSetOf(), Apod::date)
        val missing = sameDayInEarlierYears(today).filterNot(cached::contains)
        if (missing.isEmpty()) return DataResult.Success(0)

        if (!networkMonitor.isOnline.first()) return DataResult.Failure(AppError.NoConnection)

        var downloaded = 0
        for (date in missing) {
            when (val result = apodRepository.fetchApod(date)) {
                is DataResult.Success -> downloaded++
                is DataResult.Failure -> if (result.error.isRetryable) return result
            }
        }
        return DataResult.Success(downloaded)
    }

    companion object {
        /**
         * Every published day sharing [date]'s month and day, newest first, [date] itself excluded.
         * 29 February only exists in leap years, and nothing predates the first APOD.
         */
        fun sameDayInEarlierYears(date: LocalDate): List<LocalDate> {
            val monthDay = MonthDay.from(date)
            return (date.year - 1 downTo Apod.FIRST_PUBLICATION_DATE.year)
                .filter(monthDay::isValidYear)
                .map(monthDay::atYear)
                .filterNot { it.isBefore(Apod.FIRST_PUBLICATION_DATE) }
        }
    }
}
