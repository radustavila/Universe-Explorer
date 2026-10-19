package com.radustavila.nasaexplorer.data.smallbodies.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeSmallBodiesUseCase
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmallBodiesSyncWorkerTest {

    private val repository = FakeSmallBodiesRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider()

    @Test
    fun `refreshes once a day unless forced, recording the asteroids source`() = runTest {
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Success)
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Success)
        assertTrue(buildWorker(force = true).doWork() is ListenableWorker.Result.Success)

        assertEquals(2, repository.refreshCallCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.ASTEROIDS).lastSuccessAt)
    }

    @Test
    fun `a transient failure retries, a permanent one does not`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Timeout)
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Retry)

        repository.refreshResult = DataResult.Failure(AppError.EmptyResponse)
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(force: Boolean = false): SmallBodiesSyncWorker =
        TestListenableWorkerBuilder<SmallBodiesSyncWorker>(ApplicationProvider.getApplicationContext())
            .setInputData(workDataOf(SmallBodiesSyncWorker.KEY_FORCE to force))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = SmallBodiesSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeSmallBodies = SynchronizeSmallBodiesUseCase(
                            repository = repository,
                            refreshSource = RefreshSourceUseCase(metadata, clock),
                        ),
                    )
                },
            )
            .build()
}
