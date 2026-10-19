package com.radustavila.nasaexplorer.data.earthevents.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthEventRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EarthEventsSyncWorkerTest {

    private val repository = FakeEarthEventRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()

    @Test
    fun `a successful refresh reports the event count and is recorded for the EONET source`() = runTest {
        repository.refreshResult = DataResult.Success(42)

        val result = buildWorker().doWork()

        assertEquals(42, (result as ListenableWorker.Result.Success).outputData.getInt(EarthEventsSyncWorker.KEY_EVENT_COUNT, 0))
        assertNotNull(metadata.observeSourceStatus(SyncSource.EONET).first().lastSuccessAt)
    }

    @Test
    fun `a run while the cache is fresh calls nothing`() = runTest {
        buildWorker().doWork()

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, repository.refreshCallCount)
    }

    @Test
    fun `a transient failure is retried`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun `a permanent failure waits for the next period`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.EmptyResponse)

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Success)
    }

    @Test
    fun `retries stop after a few attempts`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)

        assertTrue(buildWorker(runAttemptCount = 3).doWork() is ListenableWorker.Result.Success)
    }

    private fun buildWorker(runAttemptCount: Int = 0): EarthEventsSyncWorker =
        TestListenableWorkerBuilder<EarthEventsSyncWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = EarthEventsSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        refreshEarthEvents = RefreshEarthEventsUseCase(
                            repository = repository,
                            refreshSource = RefreshSourceUseCase(metadata, clock),
                        ),
                    )
                },
            )
            .build()
}
