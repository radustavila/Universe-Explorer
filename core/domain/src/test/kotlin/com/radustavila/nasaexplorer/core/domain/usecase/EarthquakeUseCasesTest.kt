package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import com.radustavila.nasaexplorer.core.model.EarthquakePlace
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.distanceKmTo
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes.NOW
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarthquakeUseCasesTest {

    private val clock = FakeTimeProvider(currentInstant = NOW)
    private val rome = GeoCoordinate(41.9, 12.5)
    private val location = FakeObserverLocationRepository(ObserverLocation(rome, "Rome", LocationSource.MANUAL))
    private val quakes = FakeEarthquakeRepository()
    private val alerts = FakeEarthquakeAlertRepository(enabled = true)
    private val notifier = FakeEarthquakeAlertNotifier()
    private val check = CheckEarthquakeAlertUseCase(alerts, location, quakes, notifier, clock)

    /** M5.7 off Sicily, ~330 km from Rome. */
    private val nearby = TestEarthquakes.earthquake()

    @Test
    fun `a strong quake within 500 km of the saved place alerts once`() = runTest {
        quakes.setEarthquakes(listOf(nearby, TestEarthquakes.small()))

        val alert = check()

        assertEquals(nearby, alert?.earthquake)
        assertTrue(alert!!.isNearby)
        assertEquals(rome.distanceKmTo(nearby.position), alert.distanceKm, 0.001)
        assertEquals(listOf(alert), notifier.posted)
        assertEquals(nearby.time, alerts.alertedUntil)

        assertNull("announced already", check())
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a weaker or farther quake does not alert`() = runTest {
        val weak = nearby.copy(id = "weak", magnitude = 4.9)
        val far = nearby.copy(id = "far", position = GeoCoordinate(35.7, 51.4)) // Tehran
        quakes.setEarthquakes(listOf(weak, far))

        assertNull(check())
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `a tsunami flag alerts from farther away, whatever the magnitude rule`() = runTest {
        // Off Crete, ~1,000 km from Rome.
        val tsunami = nearby.copy(id = "crete", magnitude = 4.8, position = GeoCoordinate(34.9, 24.9), tsunami = true)
        quakes.setEarthquakes(listOf(tsunami))

        val alert = check()

        assertEquals("crete", alert?.earthquake?.id)
        assertFalse(alert!!.isNearby)
    }

    @Test
    fun `of several due quakes the strongest is posted and all are marked announced`() = runTest {
        val first = nearby.copy(id = "first", magnitude = 6.1, time = NOW.minus(Duration.ofHours(3)))
        val aftershock = nearby.copy(id = "after", magnitude = 5.2, time = NOW.minus(Duration.ofHours(1)))
        quakes.setEarthquakes(listOf(first, aftershock))

        assertEquals("first", check()?.earthquake?.id)
        assertEquals(aftershock.time, alerts.alertedUntil)
    }

    @Test
    fun `old quakes, a switched-off alert and a missing place stay silent`() = runTest {
        quakes.setEarthquakes(listOf(nearby.copy(time = NOW.minus(Duration.ofHours(13)))))
        assertNull("older than 12 h", check())

        quakes.setEarthquakes(listOf(nearby))
        alerts.setEnabled(false)
        assertNull("off", check())

        alerts.setEnabled(true)
        location.clearLocation()
        assertNull("no place", check())
        assertTrue(notifier.posted.isEmpty())
    }

    @Test
    fun `turning the alert on checks the cache at once`() = runTest {
        alerts.setEnabled(false)
        quakes.setEarthquakes(listOf(nearby))

        SetEarthquakeAlertEnabledUseCase(alerts, check)(true)

        assertTrue(alerts.isEnabled)
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `a refresh runs at most every 15 minutes and checks the alert after a success`() = runTest {
        val metadata = FakeSyncMetadataRepository()
        val refresh = RefreshEarthquakesUseCase(quakes, RefreshSourceUseCase(metadata, clock), check)
        quakes.setEarthquakes(listOf(nearby))
        quakes.refreshResult = DataResult.Success(1)

        assertTrue(refresh() is SourceRefreshResult.Completed)
        assertEquals(1, notifier.posted.size)

        clock.currentInstant = NOW.plus(Duration.ofMinutes(10))
        assertTrue(refresh() is SourceRefreshResult.Skipped)

        clock.currentInstant = NOW.plus(Duration.ofMinutes(16))
        quakes.refreshResult = DataResult.Failure(AppError.NoConnection)
        assertTrue(refresh() is SourceRefreshResult.Completed)
        assertEquals(2, quakes.refreshCallCount)
    }

    @Test
    fun `observed quakes leave out what no filter shows any more`() = runTest {
        val lastWeek = TestEarthquakes.small(id = "old-small", time = NOW.minus(Duration.ofDays(8)))
        val significant = TestEarthquakes.significant()
        val tooOld = TestEarthquakes.significant(id = "too-old", time = NOW.minus(Duration.ofDays(31)))
        quakes.setEarthquakes(listOf(nearby, lastWeek, significant, tooOld))

        val shown = ObserveEarthquakesUseCase(quakes, clock)().first()

        assertEquals(listOf(nearby.id, significant.id), shown.map { it.id })
    }

    @Test
    fun `filters pick by magnitude, week and significance`() {
        val significant = TestEarthquakes.significant()
        val small = TestEarthquakes.small()
        assertTrue(EarthquakeFilter.MAGNITUDE_2_5.matches(small, NOW))
        assertFalse(EarthquakeFilter.MAGNITUDE_4_5.matches(small, NOW))
        assertTrue(EarthquakeFilter.MAGNITUDE_4_5.matches(nearby, NOW))
        assertFalse("12 days old", EarthquakeFilter.MAGNITUDE_4_5.matches(significant, NOW))
        assertTrue(EarthquakeFilter.SIGNIFICANT.matches(significant, NOW))
        assertFalse(EarthquakeFilter.SIGNIFICANT.matches(nearby, NOW))
    }

    @Test
    fun `USGS place text splits into distance, direction and name`() {
        assertEquals(EarthquakePlace("Leni, Italy", 61.0, "WNW"), EarthquakePlace.parse("61 km WNW of Leni, Italy"))
        assertEquals(EarthquakePlace("central Mid-Atlantic Ridge"), EarthquakePlace.parse("central Mid-Atlantic Ridge"))
        assertNull(EarthquakePlace.parse(" "))
    }
}
