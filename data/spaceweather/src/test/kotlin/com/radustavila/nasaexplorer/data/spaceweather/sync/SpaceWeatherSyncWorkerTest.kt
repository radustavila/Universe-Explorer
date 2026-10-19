package com.radustavila.nasaexplorer.data.spaceweather.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.usecase.CheckAuroraAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshSourceUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeSpaceWeatherUseCase
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpaceWeatherSyncWorkerTest {

    private val repository = FakeSpaceWeatherRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestSpaceWeather.NOW)
    private val notifier = FakeAuroraAlertNotifier()
    private val alerts = FakeAuroraAlertRepository(AuroraAlertSettings(enabled = true, latitude = 59.3, longitude = 18.1))

    @Test
    fun `a periodic run refreshes Kp only and alerts once per storm`() = runTest {
        repository.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.0, 6.0)

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Success)
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(30))
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Success)

        assertEquals(2, repository.kpRefreshCount)
        assertEquals(0, repository.auroraRefreshCount)
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `an on-demand run from the screen also fetches the aurora map`() = runTest {
        buildWorker(includeAuroraMap = true).doWork()

        assertEquals(1, repository.auroraRefreshCount)
    }

    @Test
    fun `a transient failure retries, then gives up`() = runTest {
        repository.kpRefreshResult = DataResult.Failure(AppError.NoConnection)

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
        assertTrue(buildWorker(runAttemptCount = 3, force = true).doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun `a permanent failure does not burn retries`() = runTest {
        repository.kpRefreshResult = DataResult.Failure(AppError.EmptyResponse)

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(
        runAttemptCount: Int = 0,
        force: Boolean = false,
        includeAuroraMap: Boolean = false,
    ): SpaceWeatherSyncWorker =
        TestListenableWorkerBuilder<SpaceWeatherSyncWorker>(ApplicationProvider.getApplicationContext())
            .setRunAttemptCount(runAttemptCount)
            .setInputData(
                workDataOf(
                    SpaceWeatherSyncWorker.KEY_FORCE to force,
                    SpaceWeatherSyncWorker.KEY_INCLUDE_AURORA_MAP to includeAuroraMap,
                ),
            )
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = SpaceWeatherSyncWorker(
                        appContext = appContext,
                        workerParameters = workerParameters,
                        synchronizeSpaceWeather = SynchronizeSpaceWeatherUseCase(
                            repository = repository,
                            refreshSource = RefreshSourceUseCase(metadata, clock),
                            checkAuroraAlert = CheckAuroraAlertUseCase(alerts, repository, notifier, clock),
                            timeProvider = clock,
                        ),
                    )
                },
            )
            .build()
}
