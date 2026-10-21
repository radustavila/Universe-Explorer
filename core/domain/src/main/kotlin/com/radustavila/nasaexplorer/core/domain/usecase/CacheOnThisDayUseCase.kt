package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.model.Apod
import java.time.LocalDate
import java.time.MonthDay
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Downloads today's month and day from every earlier year of the archive that the cache does not
 * hold yet, so "On this day" works offline afterwards: one single-day request each, a few at a time,
 * and one database write for the lot.
 *
 * Runs from the sync worker after a successful sync, never from the UI. Days already cached are not
 * requested again, so after the first run of a day this costs nothing. A day NASA did not publish
 * (the archive has gaps) is skipped and remembered, so later runs do not ask for it again; a
 * connectivity-type failure stops the run, since every remaining request would fail the same way -
 * the next sync picks up where this one stopped.
 *
 * @return the number of days downloaded.
 */
class CacheOnThisDayUseCase @Inject constructor(
    private val apodRepository: ApodRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(): DataResult<Int> {
        val today = timeProvider.today()
        val cached = apodRepository.observeSameDayInEarlierYears(today).first()
            .mapTo(mutableSetOf(), Apod::date)
        val withoutEntry = syncMetadataRepository.apodDatesWithoutEntry.first()
        val missing = sameDayInEarlierYears(today).filterNot { it in cached || it in withoutEntry }
        if (missing.isEmpty()) return DataResult.Success(0)

        if (!networkMonitor.isOnline.first()) return DataResult.Failure(AppError.NoConnection)

        val batch = apodRepository.fetchApods(missing, maxConcurrent = MAX_CONCURRENT_REQUESTS)
        syncMetadataRepository.addApodDatesWithoutEntry(
            batch.failed.filterValues { it.meansNotPublished() }.keys,
        )
        val stoppedBy = batch.failed.values.firstOrNull { it.isRetryable }
        return if (stoppedBy != null) DataResult.Failure(stoppedBy) else DataResult.Success(batch.cached.size)
    }

    /** What the APOD service answers for a day it has no entry for; other failures may pass. */
    private fun AppError.meansNotPublished(): Boolean =
        this == AppError.EmptyResponse || (this is AppError.Http && code == HTTP_NOT_FOUND)

    companion object {
        /** Gentle on the shared science.nasa.gov host, still several times quicker than one by one. */
        const val MAX_CONCURRENT_REQUESTS = 4
        private const val HTTP_NOT_FOUND = 404

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
