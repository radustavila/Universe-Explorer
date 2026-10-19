package com.radustavila.nasaexplorer.data.nasa.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.AnnounceLatestApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLatestApodWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewApodNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The worker owns exactly one decision - retry or fail - and these tests pin it down, because a
 * wrong answer either wastes battery or silently stops synchronising.
 */
@RunWith(RobolectricTestRunner::class)
class ApodSyncWorkerTest {

    private val repository = FakeApodRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val widgetUpdater = FakeLatestApodWidgetUpdater()
    private val notifier = FakeNewApodNotifier()
    private val launchRepository = FakeLaunchRepository()

    @Test
    fun `a successful sync reports how many entries were written`() = runTest {
        repository.refreshResult = DataResult.Success(7)

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            7,
            (result as ListenableWorker.Result.Success).outputData
                .getInt(ApodSyncWorker.KEY_SYNCED_COUNT, 0),
        )
    }

    @Test
    fun `a successful sync announces the newest entry`() = runTest {
        val latest = TestApods.image()
        repository.setEntries(listOf(latest))
        metadata.setLastAnnouncedApodDate(latest.date.minusDays(1))

        buildWorker(notificationsEnabled = true).doWork()

        assertEquals(1, widgetUpdater.updateCallCount)
        assertEquals(listOf(latest), notifier.notified)
    }

    @Test
    fun `a failed sync announces nothing`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Http(code = 404))

        buildWorker(notificationsEnabled = true).doWork()

        assertEquals(0, widgetUpdater.updateCallCount)
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `a transient failure asks WorkManager to retry`() = runTest {
        networkMonitor.setOnline(false)

        val result = buildWorker(runAttemptCount = 0).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `a permanent failure does not burn retries and reports the reason`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Http(code = 404))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(
            AppError.Http(code = 404, message = null),
            SyncErrorCodec.decode((result as ListenableWorker.Result.Failure).outputData),
        )
    }

    @Test
    fun `every sync also refreshes the upcoming launches`() = runTest {
        buildWorker().doWork()

        assertEquals(1, launchRepository.refreshCallCount)
    }

    @Test
    fun `a launches failure neither fails nor retries the sync`() = runTest {
        launchRepository.refreshResult = DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 60))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `retries stop once the attempt budget is spent`() = runTest {
        networkMonitor.setOnline(false)

        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(
        runAttemptCount: Int = 0,
        notificationsEnabled: Boolean = false,
    ): ApodSyncWorker =
        TestListenableWorkerBuilder<ApodSyncWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: android.content.Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = ApodSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeApods = SynchronizeApodsUseCase(
                            apodRepository = repository,
                            networkMonitor = networkMonitor,
                            refreshSource = RefreshSourceUseCase(metadata, FakeTimeProvider()),
                            timeProvider = FakeTimeProvider(),
                        ),
                        announceLatestApod = AnnounceLatestApodUseCase(
                            apodRepository = repository,
                            syncMetadataRepository = metadata,
                            userPreferencesRepository = FakeUserPreferencesRepository(notificationsEnabled),
                            widgetUpdater = widgetUpdater,
                            notifier = notifier,
                        ),
                        synchronizeUpcomingLaunches = SynchronizeUpcomingLaunchesUseCase(
                            launchRepository = launchRepository,
                            networkMonitor = networkMonitor,
                        ),
                    )
                },
            )
            .build()
}
