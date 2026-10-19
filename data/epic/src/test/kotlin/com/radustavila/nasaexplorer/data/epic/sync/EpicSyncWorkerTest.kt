package com.radustavila.nasaexplorer.data.epic.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeEpicUseCase
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpicSyncWorkerTest {

    private val repository = FakeEpicRepository()
    private val networkMonitor = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()

    @Test
    fun `a successful sync reports how many photos were cached`() = runTest {
        repository.refreshResults[EpicCollection.NATURAL] = DataResult.Success(13)
        repository.refreshResults[EpicCollection.ENHANCED] = DataResult.Success(12)

        val result = buildWorker().doWork()

        assertEquals(25, (result as ListenableWorker.Result.Success).outputData.getInt(EpicSyncWorker.KEY_SYNCED_COUNT, 0))
    }

    @Test
    fun `a sync requested while EPIC is fresh succeeds without downloading`() = runTest {
        buildWorker().doWork()

        val result = buildWorker().doWork()

        assertEquals(0, (result as ListenableWorker.Result.Success).outputData.getInt(EpicSyncWorker.KEY_SYNCED_COUNT, -1))
        assertEquals(EpicCollection.entries, repository.refreshedCollections)
    }

    @Test
    fun `a transient failure asks WorkManager to retry`() = runTest {
        networkMonitor.setOnline(false)

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
    }

    @Test
    fun `a permanent failure does not burn retries and reports the reason`() = runTest {
        repository.refreshResults[EpicCollection.NATURAL] = DataResult.Failure(AppError.EmptyResponse)

        val result = buildWorker().doWork()

        assertEquals(
            AppError.EmptyResponse,
            EpicSyncErrorCodec.decode((result as ListenableWorker.Result.Failure).outputData),
        )
    }

    @Test
    fun `retries stop once the attempt budget is spent`() = runTest {
        networkMonitor.setOnline(false)

        assertTrue(buildWorker(runAttemptCount = 3).doWork() is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(runAttemptCount: Int = 0): EpicSyncWorker =
        TestListenableWorkerBuilder<EpicSyncWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = EpicSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeEpic = SynchronizeEpicUseCase(
                            epicRepository = repository,
                            networkMonitor = networkMonitor,
                            refreshSource = RefreshSourceUseCase(metadata, FakeTimeProvider()),
                        ),
                    )
                },
            )
            .build()
}
