package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheOnThisDayUseCaseTest {

    private val repository = FakeApodRepository().apply { cacheEveryFetchedDate = true }
    private val networkMonitor = FakeNetworkMonitor()
    private val timeProvider = FakeTimeProvider(currentDate = LocalDate.of(2026, 10, 19))
    private val syncMetadata = FakeSyncMetadataRepository()

    private val cacheOnThisDay = CacheOnThisDayUseCase(repository, syncMetadata, networkMonitor, timeProvider)

    @Test
    fun `the same day is listed for every earlier year of the archive, newest first`() {
        val dates = CacheOnThisDayUseCase.sameDayInEarlierYears(LocalDate.of(2026, 10, 19))

        assertEquals(31, dates.size)
        assertEquals(LocalDate.of(2025, 10, 19), dates.first())
        assertEquals(LocalDate.of(1995, 10, 19), dates.last())
    }

    @Test
    fun `days before the first APOD and missing leap days are left out`() {
        assertEquals(
            LocalDate.of(1996, 6, 1),
            CacheOnThisDayUseCase.sameDayInEarlierYears(LocalDate.of(2026, 6, 1)).last(),
        )
        assertEquals(
            listOf(2024, 2020, 2016, 2012, 2008, 2004, 2000, 1996),
            CacheOnThisDayUseCase.sameDayInEarlierYears(LocalDate.of(2028, 2, 29)).map { it.year },
        )
    }

    @Test
    fun `downloads only the days the cache does not hold yet`() = runTest {
        repository.setEntries(
            listOf(TestApods.image(date = LocalDate.of(2025, 10, 19)), TestApods.image(LocalDate.of(2001, 10, 19))),
        )

        val result = cacheOnThisDay()

        assertEquals(DataResult.Success(29), result)
        // One batch, a few requests at a time.
        assertEquals(29, repository.batchRequests.single().first.size)
        assertEquals(CacheOnThisDayUseCase.MAX_CONCURRENT_REQUESTS, repository.batchRequests.single().second)
        assertTrue(LocalDate.of(2025, 10, 19) !in repository.fetchedDates)
        assertTrue(LocalDate.of(2001, 10, 19) !in repository.fetchedDates)
        assertEquals(31, repository.observeSameDayInEarlierYears(timeProvider.today()).first().size)
    }

    @Test
    fun `a second run on the same day requests nothing`() = runTest {
        cacheOnThisDay()
        val requestsAfterFirstRun = repository.fetchedDates.size

        assertEquals(DataResult.Success(0), cacheOnThisDay())
        assertEquals(requestsAfterFirstRun, repository.fetchedDates.size)
    }

    @Test
    fun `a day NASA did not publish is skipped, the rest still downloaded`() = runTest {
        repository.fetchApodResults[LocalDate.of(2010, 10, 19)] = DataResult.Failure(AppError.Http(code = 404))

        val result = cacheOnThisDay()

        assertEquals(DataResult.Success(30), result)
        assertEquals(31, repository.fetchedDates.size)
    }

    @Test
    fun `a day NASA did not publish is remembered and not requested again`() = runTest {
        val gap = LocalDate.of(2010, 10, 19)
        val malformed = LocalDate.of(2003, 10, 19)
        repository.fetchApodResults[gap] = DataResult.Failure(AppError.Http(code = 404))
        repository.fetchApodResults[malformed] = DataResult.Failure(AppError.EmptyResponse)
        cacheOnThisDay()
        assertEquals(setOf(gap, malformed), syncMetadata.currentDatesWithoutEntry)
        val requestsAfterFirstRun = repository.fetchedDates.size

        assertEquals(DataResult.Success(0), cacheOnThisDay())
        assertEquals(requestsAfterFirstRun, repository.fetchedDates.size)
    }

    @Test
    fun `other failures are not remembered, so the next run tries again`() = runTest {
        val forbidden = LocalDate.of(2010, 10, 19)
        repository.fetchApodResults[forbidden] = DataResult.Failure(AppError.Http(code = 403))
        cacheOnThisDay()
        repository.fetchApodResults.clear()

        assertEquals(DataResult.Success(1), cacheOnThisDay())
        assertTrue(syncMetadata.currentDatesWithoutEntry.isEmpty())
        assertEquals(forbidden, repository.fetchedDates.last())
    }

    @Test
    fun `a connectivity failure stops the run instead of failing every remaining day`() = runTest {
        repository.fetchApodResults[LocalDate.of(2024, 10, 19)] = DataResult.Failure(AppError.Timeout)

        val result = cacheOnThisDay()

        assertEquals(DataResult.Failure(AppError.Timeout), result)
        assertEquals(listOf(LocalDate.of(2025, 10, 19), LocalDate.of(2024, 10, 19)), repository.fetchedDates)
        assertTrue(syncMetadata.currentDatesWithoutEntry.isEmpty())
    }

    @Test
    fun `offline nothing is requested`() = runTest {
        networkMonitor.setOnline(false)

        assertEquals(DataResult.Failure(AppError.NoConnection), cacheOnThisDay())
        assertTrue(repository.fetchedDates.isEmpty())
    }
}
