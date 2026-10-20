package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeviceLocationProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyUseCasesTest {

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T20:00:00Z"))
    private val network = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()
    private val refreshSource = RefreshSourceUseCase(metadata, clock)
    private val satellites = FakeSatelliteRepository()
    private val predictor = FakeSatellitePassPredictor(passes = listOf(TestSky.pass()))

    @Test
    fun `a device fix is saved coarsely, a denial saves nothing`() = runTest {
        val repository = FakeObserverLocationRepository()
        val provider = FakeDeviceLocationProvider(
            permissionGranted = true,
            result = DeviceLocationResult.Located(GeoCoordinate(44.426812, 26.102538)),
        )

        LocateDeviceUseCase(provider, repository)()

        assertEquals(GeoCoordinate(44.43, 26.1), repository.current?.coordinate)
        assertEquals(LocationSource.DEVICE, repository.current?.source)

        repository.clearLocation()
        provider.permissionGranted = false
        assertEquals(DeviceLocationResult.PermissionDenied, LocateDeviceUseCase(provider, repository)())
        assertNull(repository.current)
    }

    @Test
    fun `passes come from fresh elements, stale ones are flagged, missing ones give none`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val observe = ObserveIssPassesUseCase(satellites, predictor, clock, dispatcher)

        observe(TestSky.BUCHAREST).test {
            assertEquals(null, awaitItem().elements)

            satellites.setElements(TestSky.issElements(epoch = clock.currentInstant.minus(Duration.ofDays(1))))
            val fresh = awaitItem()
            assertEquals(listOf(TestSky.pass()), fresh.passes)
            assertFalse(fresh.isStale)

            satellites.setElements(TestSky.issElements(epoch = clock.currentInstant.minus(Duration.ofDays(15))))
            val stale = awaitItem()
            assertTrue(stale.isStale)
            assertTrue(stale.passes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the live dot comes from the tracker, or from the elements when offline`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        satellites.setElements(TestSky.issElements(epoch = clock.currentInstant))
        satellites.livePosition = DataResult.Success(TestSky.position(isLive = true))
        predictor.position = TestSky.position(latitude = 10.0, isLive = false)
        val observe = ObserveIssTrackUseCase(satellites, predictor, network, clock, dispatcher)

        observe().test {
            assertTrue(awaitItem().position!!.isLive)
            network.setOnline(false)
            val offline = awaitItem()
            assertFalse(offline.position!!.isLive)
            assertEquals(10.0, offline.position!!.coordinate.latitude, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, satellites.livePositionCallCount)
    }

    @Test
    fun `the ground track window moves every five minutes, not on every poll`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        satellites.setElements(TestSky.issElements(epoch = clock.currentInstant))
        predictor.position = TestSky.position()
        predictor.track = listOf(TestSky.position(), TestSky.position(longitude = 30.0))
        val observe = ObserveIssTrackUseCase(satellites, predictor, network, clock, dispatcher)

        observe().test {
            val first = awaitItem()
            clock.currentInstant = clock.currentInstant.plus(ObserveIssTrackUseCase.POLL_INTERVAL)
            val second = awaitItem()
            assertSame(first.groundTrack, second.groundTrack)
            assertEquals(1, predictor.groundTrackRequests.size)

            clock.currentInstant = Instant.parse("2026-10-19T20:05:00Z")
            awaitItem()
            assertEquals(
                listOf(
                    Instant.parse("2026-10-19T19:15:00Z"),
                    Instant.parse("2026-10-19T19:20:00Z"),
                ),
                predictor.groundTrackRequests,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `orbital elements refresh at most twice a day`() = runTest {
        val refresh = RefreshOrbitalElementsUseCase(satellites, refreshSource)

        refresh()
        val skipped = refresh()

        assertTrue(skipped is SourceRefreshResult.Skipped)
        assertEquals(1, satellites.refreshCallCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.CELESTRAK).lastSuccessAt)
    }

    @Test
    fun `an offline refresh is attempted, recorded and leaves the source due`() = runTest {
        satellites.refreshResult = DataResult.Failure(AppError.NoConnection)
        val refresh = RefreshOrbitalElementsUseCase(satellites, refreshSource)

        assertEquals(SourceRefreshResult.Completed(DataResult.Failure(AppError.NoConnection)), refresh())
        refresh()

        assertEquals(2, satellites.refreshCallCount)
        assertEquals("NoConnection", metadata.statusOf(SyncSource.CELESTRAK).lastErrorDescription)
    }

    @Test
    fun `the crew refreshes when empty, then holds pull-to-refresh to an hour`() = runTest {
        val people = FakePeopleInSpaceRepository().apply { refreshResult = DataResult.Success(10) }
        val refresh = RefreshPeopleInSpaceUseCase(people, metadata, clock, refreshSource)

        refresh()
        people.setPeople(listOf(TestSky.person()))
        val notDue = refresh()
        val forcedTooSoon = refresh(force = true)
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(61))
        val forced = refresh(force = true)

        assertTrue(notDue is SourceRefreshResult.Skipped)
        assertTrue(forcedTooSoon is SourceRefreshResult.Skipped)
        assertEquals(SourceRefreshResult.Completed(DataResult.Success(10)), forced)
        assertEquals(2, people.refreshCallCount)
    }

    @Test
    fun `an empty crew cache is refreshed even while the source is not due`() = runTest {
        val people = FakePeopleInSpaceRepository()
        val refresh = RefreshPeopleInSpaceUseCase(people, metadata, clock, refreshSource)

        refresh()
        refresh()

        assertEquals(2, people.refreshCallCount)
    }
}
