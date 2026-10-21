package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Launch Library 2's budget is 15 anonymous calls an hour; this is where the app spends it. */
class SynchronizeUpcomingLaunchesUseCaseTest {

    private val repository = FakeLaunchRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()
    private val widgets = FakeHomeWidgetUpdater()

    private val synchronizeLaunches = SynchronizeUpcomingLaunchesUseCase(
        launchRepository = repository,
        networkMonitor = networkMonitor,
        refreshSource = RefreshSourceUseCase(metadata, clock),
        widgetUpdater = widgets,
    )

    @Test
    fun `refreshes and records the launches source`() = runTest {
        repository.refreshResult = DataResult.Success(5)

        val result = synchronizeLaunches()

        assertEquals(SourceRefreshResult.Completed(DataResult.Success(5)), result)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.LAUNCHES).lastSuccessAt)
    }

    @Test
    fun `calls Launch Library at most once an hour however often the app syncs`() = runTest {
        synchronizeLaunches()
        clock.advanceBy(Duration.ofMinutes(59))
        val skipped = synchronizeLaunches()
        clock.advanceBy(Duration.ofMinutes(1))
        synchronizeLaunches()

        assertTrue(skipped is SourceRefreshResult.Skipped)
        assertEquals(2, repository.refreshCallCount)
    }

    @Test
    fun `waits out a 429's Retry-After before calling again`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 5_400))
        synchronizeLaunches()

        clock.advanceBy(Duration.ofMinutes(61))
        assertTrue(synchronizeLaunches() is SourceRefreshResult.Skipped)
        clock.advanceBy(Duration.ofMinutes(30))
        synchronizeLaunches()

        assertEquals(2, repository.refreshCallCount)
    }

    @Test
    fun `does not touch the repository while offline`() = runTest {
        networkMonitor.setOnline(false)

        val result = synchronizeLaunches()

        assertEquals(SourceRefreshResult.Completed(DataResult.Failure(AppError.NoConnection)), result)
        assertEquals(0, repository.refreshCallCount)
    }


    @Test
    fun `the next-launch widget is re-rendered after a successful refresh only`() = runTest {
        synchronizeLaunches()
        synchronizeLaunches()
        assertEquals(listOf(HomeWidget.NEXT_LAUNCH), widgets.updated)

        clock.advanceBy(Duration.ofMinutes(61))
        repository.refreshResult = DataResult.Failure(AppError.Timeout)
        synchronizeLaunches()
        assertEquals(1, widgets.updated.size)
    }

    private fun FakeTimeProvider.advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
