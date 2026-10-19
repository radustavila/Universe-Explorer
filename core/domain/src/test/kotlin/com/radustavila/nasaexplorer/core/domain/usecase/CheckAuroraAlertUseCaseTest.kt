package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather
import com.radustavila.nasaexplorer.core.testing.data.TestSpaceWeather.CURRENT_SLOT
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckAuroraAlertUseCaseTest {

    /** Stockholm: aurora from Kp 5 (G1). */
    private val stockholm = AuroraAlertSettings(enabled = true, latitude = 59.3, longitude = 18.1)

    private val alerts = FakeAuroraAlertRepository(stockholm)
    private val spaceWeather = FakeSpaceWeatherRepository()
    private val notifier = FakeAuroraAlertNotifier()
    private val clock = FakeTimeProvider(currentInstant = TestSpaceWeather.NOW)

    private val checkAlert = CheckAuroraAlertUseCase(alerts, spaceWeather, notifier, clock)

    @Test
    fun `alerts when the forecast reaches the level visible from the user's place`() = runTest {
        // Two observed slots, the current one, then 4.67 ("5-", G1) from 18:00.
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 3.67, 4.67, 5.33, 3.0)

        val alert = checkAlert()

        assertEquals(5, alert?.threshold)
        assertEquals(5.33, alert!!.expectedKp, 0.0)
        assertEquals(CURRENT_SLOT.plus(Duration.ofHours(6)), alert.startsAt)
        assertEquals(listOf(alert), notifier.notified)
    }

    @Test
    fun `stays quiet below the threshold, when disabled, or without a place`() = runTest {
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 4.33, 4.0)
        assertNull(checkAlert())

        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 6.0)
        alerts.setEnabled(false)
        assertNull(checkAlert())

        val noPlace = CheckAuroraAlertUseCase(FakeAuroraAlertRepository(AuroraAlertSettings(enabled = true)), spaceWeather, notifier, clock)
        assertNull(noPlace())
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `fires once per storm, not on every sync`() = runTest {
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 5.0, 5.67, 4.0)
        checkAlert()

        // Next syncs: the same storm, moved earlier and stretched, is still forecast.
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(30))
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 5.0, 5.0, 6.0, 5.33, 3.0)
        checkAlert()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(30))
        checkAlert()

        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `a new storm after the announced one alerts again`() = runTest {
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 5.0, 3.0)
        checkAlert()

        // The storm passed; a day later another one is forecast.
        clock.currentInstant = clock.currentInstant.plus(Duration.ofHours(24))
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(
            2.0, 2.0, 2.0, 6.0,
            from = CURRENT_SLOT.plus(Duration.ofHours(18)),
        )
        val second = checkAlert()

        assertEquals(2, notifier.notified.size)
        assertEquals(6.0, second!!.expectedKp, 0.0)
    }

    @Test
    fun `ignores storms further ahead than the lookahead`() = runTest {
        // Quiet for 27 hours from the current slot, then a storm.
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(*DoubleArray(9) { 2.0 } + 7.0, from = CURRENT_SLOT)

        assertNull(checkAlert())
    }

    @Test
    fun `turning alerts on checks the cached forecast at once and keeps the refresh running`() = runTest {
        val scheduler = FakeSpaceWeatherSyncScheduler()
        alerts.setEnabled(false)
        alerts.alertedUntilValue = CURRENT_SLOT.plus(Duration.ofHours(9))
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 5.0)

        SetAuroraAlertEnabledUseCase(alerts, scheduler, checkAlert)(enabled = true)

        assertEquals(1, notifier.notified.size)
        assertEquals(1, scheduler.periodicSyncRegistrations)
    }

    @Test
    fun `a new place re-evaluates the forecast`() = runTest {
        spaceWeather.kpForecast.value = TestSpaceWeather.forecast(2.0, 3.0, 3.33, 3.0)
        assertNull(checkAlert())

        // Tromsø sees the aurora at any Kp.
        SetAuroraAlertLocationUseCase(alerts, checkAlert)(latitude = 69.6, longitude = 18.9)

        assertEquals(0, notifier.notified.single().threshold)
    }
}
