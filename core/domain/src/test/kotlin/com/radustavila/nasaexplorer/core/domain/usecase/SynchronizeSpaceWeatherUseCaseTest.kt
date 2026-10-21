package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizeSpaceWeatherUseCaseTest {

    private val repository = FakeSpaceWeatherRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestSpaceWeather.NOW)
    private val widgets = FakeHomeWidgetUpdater()

    private val synchronize = SynchronizeSpaceWeatherUseCase(
        repository = repository,
        refreshSource = RefreshSourceUseCase(metadata, clock),
        checkAuroraAlert = CheckAuroraAlertUseCase(
            FakeAuroraAlertRepository(),
            repository,
            FakeAuroraAlertNotifier(),
            clock,
        ),
        widgetUpdater = widgets,
        timeProvider = clock,
    )

    @Test
    fun `the background run refreshes Kp and solar activity but not the aurora map`() = runTest {
        val result = synchronize()

        assertEquals(SourceRefreshResult.Completed(DataResult.Success(Unit)), result)
        assertEquals(1, repository.kpRefreshCount)
        assertEquals(0, repository.auroraRefreshCount)
        assertEquals(1, repository.solarActivityRefreshCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.SPACE_WEATHER).lastSuccessAt)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.DONKI).lastSuccessAt)
    }

    @Test
    fun `fresh sources are skipped`() = runTest {
        synchronize()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(10))

        val result = synchronize()

        assertTrue(result is SourceRefreshResult.Skipped)
        assertEquals(1, repository.kpRefreshCount)
        assertEquals(1, repository.solarActivityRefreshCount)
    }

    @Test
    fun `a stale aurora map is fetched even while Kp is fresh`() = runTest {
        synchronize()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(10))
        repository.auroraFetchedAt = clock.currentInstant.minus(Duration.ofHours(2))

        synchronize(includeAuroraMap = true)

        assertEquals(2, repository.kpRefreshCount)
        assertEquals(1, repository.auroraRefreshCount)
    }

    @Test
    fun `a fresh aurora map is not downloaded again`() = runTest {
        repository.auroraFetchedAt = clock.currentInstant.minus(Duration.ofMinutes(5))
        synchronize(includeAuroraMap = true)
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(5))

        synchronize(includeAuroraMap = true)

        assertEquals(1, repository.auroraRefreshCount)
    }

    @Test
    fun `Kp, the aurora map and solar activity are fetched side by side`() = runTest {
        repository.refreshDelayMillis = 1_000

        synchronize(includeAuroraMap = true)

        assertEquals(1, repository.kpRefreshCount)
        assertEquals(1, repository.auroraRefreshCount)
        assertEquals(1, repository.solarActivityRefreshCount)
        assertEquals(1_000, testScheduler.currentTime)
    }

    @Test
    fun `a DONKI failure does not touch the NOAA outcome or its bookkeeping`() = runTest {
        repository.solarActivityRefreshResult = DataResult.Failure(AppError.Http(code = 503))

        val result = synchronize()

        assertEquals(SourceRefreshResult.Completed(DataResult.Success(Unit)), result)
        assertTrue(metadata.statusOf(SyncSource.DONKI).isFailing)
        assertTrue(!metadata.statusOf(SyncSource.SPACE_WEATHER).isFailing)
    }

    @Test
    fun `a Kp failure is reported even when the map succeeded`() = runTest {
        repository.kpRefreshResult = DataResult.Failure(AppError.Timeout)

        val result = synchronize(includeAuroraMap = true)

        assertEquals(SourceRefreshResult.Completed(DataResult.Failure(AppError.Timeout)), result)
        assertTrue(metadata.statusOf(SyncSource.SPACE_WEATHER).isFailing)
    }

    @Test
    fun `the aurora widget is re-rendered after a good Kp refresh only`() = runTest {
        synchronize()
        assertEquals(listOf(HomeWidget.AURORA), widgets.updated)

        // Skipped while fresh: nothing new to show.
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(10))
        synchronize()
        assertEquals(1, widgets.updated.size)

        clock.currentInstant = clock.currentInstant.plus(SynchronizeSpaceWeatherUseCase.KP_INTERVAL)
        repository.kpRefreshResult = DataResult.Failure(AppError.Timeout)
        synchronize()
        assertEquals(1, widgets.updated.size)
    }
}
