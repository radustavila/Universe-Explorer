package com.radustavila.nasaexplorer.data.exoplanets.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeExoplanetsUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExoplanetSyncWorkerTest {

    private val repository = FakeExoplanetRepository().apply {
        planetsOnRefresh = listOf(TestExoplanets.exoplanet(), TestExoplanets.proximaB)
    }
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()

    @Test
    fun `a refresh reports how many planets were stored`() = runTest {
        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            2,
            (result as ListenableWorker.Result.Success).outputData.getInt(ExoplanetSyncWorker.KEY_STORED_COUNT, 0),
        )
    }

    @Test
    fun `a fresh catalogue is skipped successfully unless forced`() = runTest {
        buildWorker().doWork()

        val skipped = buildWorker().doWork()
        val forced = buildWorker(force = true).doWork()

        assertTrue(skipped is ListenableWorker.Result.Success)
        assertTrue(forced is ListenableWorker.Result.Success)
        assertEquals(2, repository.refreshCallCount)
    }

    @Test
    fun `a transient failure retries, then gives up`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
        assertTrue(buildWorker(runAttemptCount = 3).doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun `a permanent failure does not burn retries`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.EmptyResponse)

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(runAttemptCount: Int = 0, force: Boolean = false): ExoplanetSyncWorker =
        TestListenableWorkerBuilder<ExoplanetSyncWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .setInputData(workDataOf(ExoplanetSyncWorker.KEY_FORCE to force))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = ExoplanetSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeExoplanets = SynchronizeExoplanetsUseCase(
                            repository = repository,
                            refreshSource = RefreshSourceUseCase(metadata, clock),
                        ),
                    )
                },
            )
            .build()
}
