package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshSourceUseCaseTest {

    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val refreshSource = RefreshSourceUseCase(metadata, clock)
    private var calls = 0

    private suspend fun refreshLaunches(
        result: DataResult<Int> = DataResult.Success(3),
        force: Boolean = false,
    ) = refreshSource(SyncSource.LAUNCHES, minInterval = Duration.ofHours(1), force = force) {
        calls++
        result
    }

    @Test
    fun `a success is recorded and the source is not due again until its interval has passed`() =
        runTest {
            assertEquals(SourceRefreshResult.Completed(DataResult.Success(3)), refreshLaunches())

            clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(59))
            val skipped = refreshLaunches()
            clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(1))
            val dueAgain = refreshLaunches()

            assertEquals(SourceRefreshResult.Skipped(clock.currentInstant), skipped)
            assertEquals(SourceRefreshResult.Completed(DataResult.Success(3)), dueAgain)
            assertEquals(2, calls)
            assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.LAUNCHES).lastSuccessAt)
        }

    @Test
    fun `force ignores the interval`() = runTest {
        refreshLaunches()

        refreshLaunches(force = true)

        assertEquals(2, calls)
    }

    @Test
    fun `a rate-limited failure blocks the source until Retry-After`() = runTest {
        refreshLaunches(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 600)))

        val status = metadata.statusOf(SyncSource.LAUNCHES)
        assertEquals(clock.currentInstant.plusSeconds(600), status.nextAllowedRefreshAt)
        assertEquals("RateLimited(retryAfterSeconds=600)", status.lastErrorDescription)
        assertEquals(SourceRefreshResult.Skipped(clock.currentInstant.plusSeconds(600)), refreshLaunches())
    }

    @Test
    fun `any other failure leaves the source due`() = runTest {
        refreshLaunches()
        refreshLaunches(DataResult.Failure(AppError.Timeout), force = true)

        assertNull(metadata.statusOf(SyncSource.LAUNCHES).nextAllowedRefreshAt)
        refreshLaunches()
        assertEquals(3, calls)
    }
}
