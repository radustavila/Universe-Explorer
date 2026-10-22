package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeCloudForecastRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeDarkSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.TestStargazing
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StargazingUseCasesTest {

    private val noon = Instant.parse("2026-09-10T12:00:00Z")
    private val clock = FakeTimeProvider(currentInstant = noon)
    private val bucharest = GeoCoordinate(44.43, 26.1)
    private val location =
        FakeObserverLocationRepository(ObserverLocation(bucharest, "Bucharest", LocationSource.MANUAL))
    private val tonight = TestStargazing.darkNight(darkStart = Instant.parse("2026-09-10T20:00:00Z"))
    private val tomorrowNight = TestStargazing.darkNight(darkStart = Instant.parse("2026-09-11T20:00:00Z"))
    private val calculator = FakeDarkSkyCalculator(listOf(tonight, tomorrowNight))
    private val forecasts = FakeCloudForecastRepository(TestStargazing.cloudForecast(from = noon) { 0.0 })
    private val alerts = FakeStargazingAlertRepository(StargazingAlertSettings(enabled = true, minimumScore = 70))
    private val notifier = FakeStargazingAlertNotifier()
    private val scheduler = FakeStargazingAlertScheduler()
    private val check = CheckStargazingAlertUseCase(alerts, location, forecasts, calculator, notifier, clock)

    @Test
    fun `the outlook scores each night from the cached forecast`() = runTest {
        // Clear tonight, overcast tomorrow night (hours 32 onwards).
        forecasts.forecast.value = TestStargazing.cloudForecast(from = noon) { hour -> if (hour < 30) 0.0 else 100.0 }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val observe = ObserveStargazingForecastUseCase(forecasts, calculator, clock, dispatcher)

        observe(bucharest, nights = 2).test {
            val outlook = awaitItem()
            assertEquals(listOf(100, 0), outlook.nights.map { it.score })
            assertEquals(noon, outlook.fetchedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a clear night is announced once`() = runTest {
        val alert = checkNotNull(check())
        assertEquals(100, alert.score)
        assertEquals(tonight.darkStart, alert.window.start)
        assertEquals(tonight.sunrise, alerts.alertedUntilValue)

        // Later checks the same night, even after midnight, stay silent.
        clock.currentInstant = Instant.parse("2026-09-10T23:00:00Z")
        assertNull(check())
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `the next clear night is announced again`() = runTest {
        check()
        clock.currentInstant = Instant.parse("2026-09-11T12:00:00Z")
        calculator.nights = listOf(tomorrowNight)
        forecasts.forecast.value = TestStargazing.cloudForecast(from = clock.currentInstant) { 0.0 }

        assertNotNull(check())
        assertEquals(2, notifier.notified.size)
    }

    @Test
    fun `no alert below the chosen score, while off, or without a place`() = runTest {
        forecasts.forecast.value = TestStargazing.cloudForecast(from = noon) { 50.0 }
        assertNull(check())

        forecasts.forecast.value = TestStargazing.cloudForecast(from = noon) { 0.0 }
        alerts.setEnabled(false)
        assertNull(check())

        alerts.setEnabled(true)
        location.clearLocation()
        assertNull(check())
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `no alert once the clear window has all but passed`() = runTest {
        // Clear all night, but darkness ends at 04:00 UTC and it is now 03:40.
        clock.currentInstant = Instant.parse("2026-09-11T03:40:00Z")

        assertNull(check())
    }

    @Test
    fun `the background check refreshes the forecast for the saved place, then checks`() = runTest {
        val metadata = FakeSyncMetadataRepository()
        val refresh = RefreshCloudForecastUseCase(forecasts, RefreshSourceUseCase(metadata, clock))
        val run = RunStargazingAlertCheckUseCase(alerts, location, refresh, check)

        val outcome = run()

        assertEquals(SourceRefreshResult.Completed(DataResult.Success(Unit)), outcome)
        assertEquals(listOf(bucharest), forecasts.refreshRequests)
        assertEquals(noon, metadata.statusOf(SyncSource.MET_NORWAY).lastSuccessAt)
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `the background check sends nothing while the alert is off, and checks the cache when offline`() = runTest {
        val metadata = FakeSyncMetadataRepository()
        val refresh = RefreshCloudForecastUseCase(forecasts, RefreshSourceUseCase(metadata, clock))
        val run = RunStargazingAlertCheckUseCase(alerts, location, refresh, check)

        alerts.setEnabled(false)
        assertNull(run())
        assertTrue(forecasts.refreshRequests.isEmpty())

        alerts.setEnabled(true)
        forecasts.refreshResult = DataResult.Failure(AppError.NoConnection)
        run()
        assertTrue(metadata.statusOf(SyncSource.MET_NORWAY).isFailing)
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `a rate-limited refresh holds MET Norway back until Retry-After`() = runTest {
        val metadata = FakeSyncMetadataRepository()
        val refresh = RefreshCloudForecastUseCase(forecasts, RefreshSourceUseCase(metadata, clock))
        forecasts.refreshResult = DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 600))

        refresh(bucharest)
        val second = refresh(bucharest)

        assertEquals(SourceRefreshResult.Skipped(noon.plus(Duration.ofMinutes(10))), second)
        assertEquals(1, forecasts.refreshRequests.size)
    }

    @Test
    fun `switching the alert on schedules the check and checks at once, off cancels it`() = runTest {
        alerts.setEnabled(false)
        val set = SetStargazingAlertEnabledUseCase(alerts, scheduler, check)

        set(true)
        assertTrue(scheduler.isPeriodicCheckScheduled)
        assertEquals(1, notifier.notified.size)

        set(false)
        assertFalse(scheduler.isPeriodicCheckScheduled)
    }

    @Test
    fun `app start brings the background check in line with the setting`() = runTest {
        ScheduleStargazingAlertUseCase(alerts, scheduler)()
        assertTrue(scheduler.isPeriodicCheckScheduled)

        alerts.setEnabled(false)
        ScheduleStargazingAlertUseCase(alerts, scheduler)()
        assertFalse(scheduler.isPeriodicCheckScheduled)
    }

    @Test
    fun `the minimum score is stored`() = runTest {
        SetStargazingAlertMinimumScoreUseCase(alerts)(75)
        alerts.settings.test {
            assertEquals(75, awaitItem().minimumScore)
        }
    }
}
