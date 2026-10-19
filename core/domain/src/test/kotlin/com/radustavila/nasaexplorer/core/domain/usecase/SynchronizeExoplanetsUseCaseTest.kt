package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizeExoplanetsUseCaseTest {

    private val repository = FakeExoplanetRepository().apply {
        planetsOnRefresh = listOf(TestExoplanets.exoplanet(), TestExoplanets.hotJupiter)
    }
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val synchronize = SynchronizeExoplanetsUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `refreshes at most weekly and records the outcome under the exoplanet source`() = runTest {
        assertEquals(SourceRefreshResult.Completed(DataResult.Success(2)), synchronize())

        clock.currentInstant = clock.currentInstant.plus(Duration.ofDays(6))
        val skipped = synchronize()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofDays(1))
        val dueAgain = synchronize()

        assertTrue(skipped is SourceRefreshResult.Skipped)
        assertTrue(dueAgain is SourceRefreshResult.Completed)
        assertEquals(2, repository.refreshCallCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.EXOPLANETS).lastSuccessAt)
    }

    @Test
    fun `force refreshes a catalogue that is still fresh`() = runTest {
        synchronize()

        synchronize(force = true)

        assertEquals(2, repository.refreshCallCount)
    }

    @Test
    fun `a failure stays due so the next request tries again`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        synchronize()

        repository.refreshResult = null
        val retried = synchronize()

        assertEquals(SourceRefreshResult.Completed(DataResult.Success(2)), retried)
    }

    @Test
    fun `sync status combines the running job with the recorded outcome`() = runTest {
        val scheduler = FakeExoplanetSyncScheduler()
        val observe = ObserveExoplanetSyncStatusUseCase(scheduler, metadata)

        observe().test {
            assertEquals(ExoplanetSyncStatus(), awaitItem())

            scheduler.setSyncing(true)
            assertTrue(awaitItem().isSyncing)

            repository.refreshResult = DataResult.Failure(AppError.Timeout)
            synchronize()
            assertTrue(awaitItem().lastAttemptFailed)

            scheduler.setSyncing(false)
            val idle = awaitItem()
            assertFalse(idle.isSyncing)
            assertTrue(idle.lastAttemptFailed)
        }
    }
}
