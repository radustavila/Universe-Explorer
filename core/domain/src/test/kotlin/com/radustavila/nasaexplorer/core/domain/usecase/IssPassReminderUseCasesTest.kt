package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssPassReminderUseCasesTest {

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T20:00:00Z"))
    private val preferences = FakeSkyPreferencesRepository()
    private val location = FakeObserverLocationRepository(TestSky.location())
    private val satellites = FakeSatelliteRepository().apply { setElements(TestSky.issElements()) }
    private val visiblePass = TestSky.pass(start = Instant.parse("2026-10-20T02:07:31Z"))
    private val predictor = FakeSatellitePassPredictor(
        passes = listOf(
            TestSky.pass(start = Instant.parse("2026-10-20T00:30:00Z"), isVisible = false),
            visiblePass,
        ),
    )
    private val scheduler = FakeIssPassReminderScheduler()
    private val notifier = FakeIssPassNotifier()

    private val reschedule = RescheduleIssPassReminderUseCase(
        preferences, location, satellites, predictor, scheduler, clock,
    )
    private val remind = RemindOfIssPassUseCase(
        preferences = preferences,
        locationRepository = location,
        satelliteRepository = satellites,
        refreshOrbitalElements = RefreshOrbitalElementsUseCase(
            satellites,
            RefreshSourceUseCase(FakeSyncMetadataRepository(), clock),
            FakeHomeWidgetUpdater(),
        ),
        predictor = predictor,
        notifier = notifier,
        rescheduleReminder = reschedule,
        timeProvider = clock,
    )

    @Test
    fun `turning reminders on arms a wake-up ten minutes before the next visible pass`() = runTest {
        SetIssPassRemindersEnabledUseCase(preferences, reschedule)(true)

        assertEquals(visiblePass.start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)
    }

    @Test
    fun `turning reminders off cancels the wake-up`() = runTest {
        SetIssPassRemindersEnabledUseCase(preferences, reschedule)(true)

        SetIssPassRemindersEnabledUseCase(preferences, reschedule)(false)

        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `without a location or a visible pass it checks again later`() = runTest {
        preferences.setIssPassRemindersEnabled(true)
        location.clearLocation()

        reschedule()

        assertEquals(clock.currentInstant.plus(RescheduleIssPassReminderUseCase.RECHECK_INTERVAL), scheduler.scheduledAt)
    }

    @Test
    fun `stale elements are not used`() = runTest {
        preferences.setIssPassRemindersEnabled(true)
        satellites.setElements(TestSky.issElements(epoch = clock.currentInstant.minus(Duration.ofDays(20))))

        reschedule()

        assertEquals(clock.currentInstant.plus(RescheduleIssPassReminderUseCase.RECHECK_INTERVAL), scheduler.scheduledAt)
    }

    @Test
    fun `the wake-up posts the imminent pass once and arms the next one`() = runTest {
        val nextVisible = TestSky.pass(start = Instant.parse("2026-10-20T03:44:45Z"))
        predictor.passes = predictor.passes + nextVisible
        preferences.setIssPassRemindersEnabled(true)
        clock.currentInstant = visiblePass.start.minus(Duration.ofMinutes(10))

        assertEquals(visiblePass, remind())
        assertNull(remind())

        assertEquals(listOf(visiblePass), notifier.notified)
        assertEquals(nextVisible.start.minus(Duration.ofMinutes(10)), scheduler.scheduledAt)
    }

    @Test
    fun `a wake-up after reminders were turned off posts nothing`() = runTest {
        clock.currentInstant = visiblePass.start.minus(Duration.ofMinutes(10))

        assertNull(remind())

        assertTrue(notifier.notified.isEmpty())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `an invisible pass is never announced`() = runTest {
        preferences.setIssPassRemindersEnabled(true)
        clock.currentInstant = Instant.parse("2026-10-20T00:25:00Z")

        assertNull(remind())
    }
}
