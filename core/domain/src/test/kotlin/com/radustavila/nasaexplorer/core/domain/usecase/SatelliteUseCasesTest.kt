package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteSetStatus
import com.radustavila.nasaexplorer.core.domain.sky.StarlinkTrains
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteUseCasesTest {

    private val now = Instant.parse("2026-10-21T18:00:00Z")
    private val clock = FakeTimeProvider(currentInstant = now)
    private val metadata = FakeSyncMetadataRepository()
    private val refreshSource = RefreshSourceUseCase(metadata, clock)
    private val preferences = FakeSkyPreferencesRepository()
    private val location = FakeObserverLocationRepository(TestSky.location())
    private val satellites = FakeSatelliteRepository()

    private val hubble = TestSatellites.hubbleElements()
    private val tiangong = TestSatellites.tiangongElements()
    private val brightRocketBody = TestSatellites.hubbleElements().copy(noradId = 694, name = "ATLAS CENTAUR 2")
    private val train = listOf(-10.0, 0.0, 10.0).mapIndexed { index, alongTrack ->
        val piece = "ABC"[index].toString()
        TestSatellites.starlinkElements(noradId = 101_000 + index, piece = piece, alongTrack = alongTrack)
    }

    private val hubblePass = TestSky.pass(start = now.plus(Duration.ofHours(2)))
    private val tiangongPass = TestSky.pass(start = now.plus(Duration.ofHours(1)))
    private val brightPass = TestSky.pass(start = now.plus(Duration.ofMinutes(30)))
    private val trainPass = TestSky.pass(start = now.plus(Duration.ofHours(3)))
    private val predictor = FakeSatellitePassPredictor(
        passesByNoradId = mapOf(
            hubble.noradId to listOf(hubblePass),
            tiangong.noradId to listOf(tiangongPass),
            brightRocketBody.noradId to listOf(brightPass),
            OrbitalElements.ISS_NORAD_ID to listOf(TestSky.pass(start = now.plus(Duration.ofMinutes(10)))),
            // The middle of the train.
            101_001 to listOf(trainPass),
        ),
    )
    private val scheduler = FakeSatellitePassReminderScheduler()
    private val notifier = FakeSatellitePassNotifier()
    private val refreshSets = RefreshSatelliteSetsUseCase(preferences, satellites, refreshSource)
    private val reschedule =
        RescheduleSatellitePassReminderUseCase(preferences, location, satellites, predictor, scheduler, clock)
    private val remind = RemindOfSatellitePassUseCase(
        preferences = preferences,
        locationRepository = location,
        satelliteRepository = satellites,
        refreshSatelliteSets = refreshSets,
        predictor = predictor,
        notifier = notifier,
        rescheduleReminder = reschedule,
        timeProvider = clock,
    )

    @Test
    fun `Starlink satellites are grouped by launch and only young launches are trains`() {
        val oldLaunch = listOf(
            TestSatellites.starlinkElements(noradId = 100_855, launch = "2026-225", launchedDaysAgo = 15.0),
            TestSatellites.starlinkElements(
                noradId = 100_856,
                launch = "2026-225",
                piece = "B",
                launchedDaysAgo = 15.0,
            ),
        )
        val unknown = TestSatellites.starlinkElements(noradId = 1).copy(internationalDesignator = null)

        val trains = StarlinkTrains.from(train + oldLaunch + unknown, now)

        val young = trains.single()
        assertEquals("2026-240", young.train?.launch)
        assertEquals(3, young.train?.satelliteCount)
        // Whole orbits only: within one orbit and a half of the true launch.
        val launchError = Duration.between(Instant.parse("2026-10-18T12:00:00Z"), young.train?.launchedAt).abs()
        assertTrue("$launchError", launchError < Duration.ofHours(2))
        assertEquals(101_001, young.noradId)
        assertEquals(SatelliteSet.STARLINK_TRAINS, young.set)
    }

    @Test
    fun `each enabled set is fetched at most once a day, the ISS never`() = runTest {
        preferences.setSatelliteSetEnabled(SatelliteSet.HUBBLE, true)
        preferences.setSatelliteSetEnabled(SatelliteSet.BRIGHT_SATELLITES, true)

        refreshSets()
        refreshSets()
        refreshSets(setOf(SatelliteSet.ISS))
        assertEquals(listOf(SatelliteSet.HUBBLE, SatelliteSet.BRIGHT_SATELLITES), satellites.refreshedSets)

        clock.currentInstant = now.plus(Duration.ofHours(23))
        refreshSets()
        assertEquals(2, satellites.refreshedSets.size)

        clock.currentInstant = now.plus(Duration.ofHours(24))
        refreshSets()
        refreshSets()
        assertEquals(4, satellites.refreshedSets.size)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.CELESTRAK_VISUAL).lastSuccessAt)
    }

    @Test
    fun `a failed fetch leaves the set due`() = runTest {
        satellites.refreshSetResult = DataResult.Failure(AppError.NoConnection)

        refreshSets(setOf(SatelliteSet.TIANGONG))
        refreshSets(setOf(SatelliteSet.TIANGONG))

        assertEquals(2, satellites.refreshedSets.size)
    }

    @Test
    fun `turning a set on fetches it, turning it off does not`() = runTest {
        val setEnabled = SetSatelliteSetEnabledUseCase(preferences, refreshSets, reschedule)

        setEnabled(SatelliteSet.HUBBLE, true)
        setEnabled(SatelliteSet.HUBBLE, false)

        assertEquals(listOf(SatelliteSet.HUBBLE), satellites.refreshedSets)
    }

    @Test
    fun `passes of the enabled sets are merged soonest first, without the ISS or duplicates`() = runTest {
        enable(SatelliteSet.HUBBLE, SatelliteSet.TIANGONG, SatelliteSet.STARLINK_TRAINS, SatelliteSet.BRIGHT_SATELLITES)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(hubble))
        satellites.setSet(SatelliteSet.TIANGONG, listOf(tiangong))
        satellites.setSet(SatelliteSet.STARLINK_TRAINS, train)
        satellites.setSet(
            SatelliteSet.BRIGHT_SATELLITES,
            listOf(brightRocketBody, hubble, TestSky.issElements(epoch = now)),
        )

        observe().test {
            val forecast = awaitItem()
            assertEquals(
                listOf(
                    SatelliteSet.BRIGHT_SATELLITES,
                    SatelliteSet.TIANGONG,
                    SatelliteSet.HUBBLE,
                    SatelliteSet.STARLINK_TRAINS,
                ),
                forecast.passes.map { it.satellite.set },
            )
            assertEquals(listOf(brightPass, tiangongPass, hubblePass, trainPass), forecast.passes.map { it.pass })
            assertEquals(3, forecast.passes.last().satellite.train?.satelliteCount)
            assertTrue(forecast.setStatus.values.all { it == SatelliteSetStatus.READY })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a set without elements, with old ones or without a recent launch says why`() = runTest {
        enable(SatelliteSet.HUBBLE, SatelliteSet.TIANGONG, SatelliteSet.STARLINK_TRAINS)
        val oldTiangong = TestSatellites.tiangongElements(epoch = now.minus(Duration.ofDays(15)))
        satellites.setSet(SatelliteSet.TIANGONG, listOf(oldTiangong))
        val oldTrain = TestSatellites.starlinkElements(noradId = 7, launchedDaysAgo = 12.0)
        satellites.setSet(SatelliteSet.STARLINK_TRAINS, listOf(oldTrain))

        observe().test {
            val forecast = awaitItem()
            assertTrue(forecast.passes.isEmpty())
            assertEquals(
                mapOf(
                    SatelliteSet.HUBBLE to SatelliteSetStatus.NO_DATA,
                    SatelliteSet.TIANGONG to SatelliteSetStatus.STALE,
                    SatelliteSet.STARLINK_TRAINS to SatelliteSetStatus.NO_RECENT_LAUNCH,
                ),
                forecast.setStatus,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `passes that are over drop out of the list between full searches`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        enable(SatelliteSet.BRIGHT_SATELLITES)
        satellites.setSet(SatelliteSet.BRIGHT_SATELLITES, listOf(brightRocketBody))
        val observe = ObserveSatellitePassesUseCase(preferences, satellites, predictor, clock, dispatcher)

        observe(TestSky.BUCHAREST).test {
            assertEquals(listOf(brightPass), awaitItem().passes.map { it.pass })

            clock.currentInstant = brightPass.end.plusSeconds(1)
            testScheduler.advanceTimeBy(ObserveSatellitePassesUseCase.PRUNE_INTERVAL.toMillis() + 1)

            assertTrue(awaitItem().passes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reminder is armed ten minutes before the soonest pass of a set with reminders on`() = runTest {
        enable(SatelliteSet.HUBBLE, SatelliteSet.TIANGONG)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(hubble))
        satellites.setSet(SatelliteSet.TIANGONG, listOf(tiangong))
        val setReminder = SetSatellitePassReminderEnabledUseCase(preferences, reschedule)

        setReminder(SatelliteSet.HUBBLE, true)
        assertEquals(hubblePass.start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)

        setReminder(SatelliteSet.TIANGONG, true)
        assertEquals(tiangongPass.start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)
    }

    @Test
    fun `no reminder for a set turned off, for the bright satellites or with every reminder off`() = runTest {
        enable(SatelliteSet.BRIGHT_SATELLITES)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(hubble))
        satellites.setSet(SatelliteSet.BRIGHT_SATELLITES, listOf(brightRocketBody))
        val setReminder = SetSatellitePassReminderEnabledUseCase(preferences, reschedule)

        setReminder(SatelliteSet.BRIGHT_SATELLITES, true)
        // Hubble's reminder counts only while Hubble is in the list.
        setReminder(SatelliteSet.HUBBLE, true)

        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `the wake-up posts every pass starting soon, once`() = runTest {
        enable(SatelliteSet.HUBBLE, SatelliteSet.TIANGONG)
        preferences.setSatellitePassRemindersEnabled(SatelliteSet.HUBBLE, true)
        preferences.setSatellitePassRemindersEnabled(SatelliteSet.TIANGONG, true)
        satellites.setSet(SatelliteSet.HUBBLE, listOf(hubble))
        satellites.setSet(SatelliteSet.TIANGONG, listOf(tiangong))
        val hubbleSoon = tiangongPass.copy(start = tiangongPass.start.plusSeconds(120))
        predictor.passesByNoradId += hubble.noradId to listOf(hubbleSoon)
        clock.currentInstant = tiangongPass.start.minus(Duration.ofMinutes(9))

        val posted = remind()

        assertEquals(listOf(SatelliteSet.TIANGONG, SatelliteSet.HUBBLE), posted.map { it.satellite.set })
        assertEquals(posted, notifier.notified)
        assertTrue(remind().isEmpty())
        assertEquals(2, notifier.notified.size)
        // Nothing else in the forecast: look again later.
        val recheck = clock.currentInstant.plus(RescheduleIssPassReminderUseCase.RECHECK_INTERVAL)
        assertEquals(recheck, scheduler.scheduledAt)
    }

    private suspend fun enable(vararg sets: SatelliteSet) {
        sets.forEach { preferences.setSatelliteSetEnabled(it, true) }
    }

    private fun kotlinx.coroutines.test.TestScope.observe() =
        ObserveSatellitePassesUseCase(preferences, satellites, predictor, clock, StandardTestDispatcher(testScheduler))
            .invoke(TestSky.BUCHAREST)
}
