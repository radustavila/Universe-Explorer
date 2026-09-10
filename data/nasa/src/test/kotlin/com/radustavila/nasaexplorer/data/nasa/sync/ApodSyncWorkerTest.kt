package com.radustavila.nasaexplorer.data.nasa.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeApodsUseCase
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
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
    fun `retries stop once the attempt budget is spent`() = runTest {
        networkMonitor.setOnline(false)

        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(runAttemptCount: Int = 0): ApodSyncWorker =
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
                            syncMetadataRepository = metadata,
                            networkMonitor = networkMonitor,
                            timeProvider = FakeTimeProvider(),
                        ),
                    )
                },
            )
            .build()
}
