package com.radustavila.nasaexplorer.data.mars.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeMarsPhotosUseCase
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarsSyncWorkerTest {

    private val repository = FakeMarsPhotoRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val time = FakeTimeProvider()

    @Test
    fun `a successful refresh reports how many photos were stored`() = runTest {
        repository.refreshResult = DataResult.Success(13)

        val result = buildWorker().doWork()

        val output = (result as ListenableWorker.Result.Success).outputData
        assertEquals(13, output.getInt(MarsSyncWorker.KEY_STORED_COUNT, 0))
        assertEquals(time.now(), metadata.statusOf(SyncSource.MARS).lastSuccessAt)
    }

    @Test
    fun `a fresh cache is not downloaded again`() = runTest {
        buildWorker().doWork()

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, repository.refreshCount)
    }

    @Test
    fun `a forced refresh ignores the daily interval`() = runTest {
        buildWorker().doWork()

        buildWorker(force = true).doWork()

        assertEquals(2, repository.refreshCount)
    }

    @Test
    fun `a timeout is retried`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun `a feed that is down fails without burning retries`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Http(code = 403))

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun `retries stop after the last attempt`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)

        assertTrue(buildWorker(runAttemptCount = 3).doWork() is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(force: Boolean = false, runAttemptCount: Int = 0): MarsSyncWorker =
        TestListenableWorkerBuilder<MarsSyncWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(workDataOf(MarsSyncWorker.KEY_FORCE to force))
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = MarsSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeMarsPhotos = SynchronizeMarsPhotosUseCase(
                            repository = repository,
                            refreshSource = RefreshSourceUseCase(metadata, time),
                        ),
                    )
                },
            )
            .build()
}
