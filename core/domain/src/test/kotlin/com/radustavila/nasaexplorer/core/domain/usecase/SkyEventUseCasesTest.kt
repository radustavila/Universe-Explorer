package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminder
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocalSolarEclipse
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonQuarter
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventCalendar
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkyEventUseCasesTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-08-01T12:00:00Z"))
    private val location = FakeObserverLocationRepository(TestSky.location())
    private val eclipse = TestSkyEvents.totalSolarEclipse()

    /** From Bucharest the eclipse cannot be seen; anywhere else it starts at the test data's first contact. */
    private val calendar = FakeSkyEventCalendar { observer ->
        val august = TestSkyEvents.august2026()
        if (observer != TestSky.BUCHAREST) return@FakeSkyEventCalendar august
        august.map { if (it.id == eclipse.id) eclipse.copy(local = null) else it }
    }
    private val reminders = FakeSkyEventReminderRepository()
    private val scheduler = FakeSkyEventReminderScheduler()
    private val notifier = FakeSkyEventNotifier()

    private val reschedule = RescheduleSkyEventRemindersUseCase(
        reminders,
        calendar,
        location,
        scheduler,
        clock,
        dispatcher,
    )
    private val setReminder = SetSkyEventReminderUseCase(reminders, reschedule)
    private val remind = RemindOfSkyEventsUseCase(
        reminders,
        calendar,
        location,
        notifier,
        reschedule,
        clock,
        dispatcher,
    )

    @Test
    fun `a reminder arms a wake-up its lead time before the event starts`() = scope.runTest {
        location.setLocation(TestSky.location(coordinate = ZARAGOZA))

        setReminder(eclipse.id, SkyEventReminderLead.ONE_HOUR)

        val firstContact = checkNotNull(eclipse.local).partialStart
        assertEquals(firstContact.minus(Duration.ofHours(1)), scheduler.scheduledAt)
        assertEquals(SkyEventReminder(eclipse.id, SkyEventReminderLead.ONE_HOUR), reminders.current[eclipse.id])
    }

    @Test
    fun `the soonest of several reminders is armed, and removing the last cancels`() = scope.runTest {
        val perseids = TestSkyEvents.perseids()
        setReminder(perseids.id, SkyEventReminderLead.ONE_DAY)
        setReminder(eclipse.id, SkyEventReminderLead.FIFTEEN_MINUTES)

        // Bucharest: the eclipse is not visible, so it counts from its global peak, after the
        // Perseids' day-ahead reminder.
        assertEquals(perseids.start.minus(Duration.ofDays(1)), scheduler.scheduledAt)

        setReminder(perseids.id, null)
        assertEquals(eclipse.time.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)
        setReminder(eclipse.id, null)
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `a new place moves the wake-up to the local first contact`() = scope.runTest {
        setReminder(eclipse.id, SkyEventReminderLead.FIFTEEN_MINUTES)
        assertEquals(eclipse.time.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)

        val watcher = launch { RescheduleSkyEventRemindersOnLocationChangeUseCase(location, reminders, reschedule)() }
        runCurrent()
        location.setLocation(TestSky.location(coordinate = ZARAGOZA, name = "Zaragoza"))
        advanceUntilIdle()

        val local: LocalSolarEclipse = checkNotNull(eclipse.local)
        assertEquals(local.partialStart.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)
        watcher.cancel()
    }

    @Test
    fun `at app start the watcher re-arms restored reminders, and does nothing without any`() = scope.runTest {
        val watcher = launch { RescheduleSkyEventRemindersOnLocationChangeUseCase(location, reminders, reschedule)() }
        advanceUntilIdle()
        assertNull(scheduler.scheduledAt)
        watcher.cancel()

        // Restored from a backup: the reminder is there, the WorkManager job is not.
        reminders.setReminder(SkyEventReminder(eclipse.id, SkyEventReminderLead.ONE_HOUR))
        val restarted = launch { RescheduleSkyEventRemindersOnLocationChangeUseCase(location, reminders, reschedule)() }
        advanceUntilIdle()
        assertEquals(eclipse.time.minus(Duration.ofHours(1)), scheduler.scheduledAt)
        restarted.cancel()
    }

    @Test
    fun `the wake-up posts the due reminder once, forgets it, and arms the next`() = scope.runTest {
        val perseids = TestSkyEvents.perseids()
        location.setLocation(TestSky.location(coordinate = ZARAGOZA))
        setReminder(eclipse.id, SkyEventReminderLead.ONE_HOUR)
        setReminder(perseids.id, SkyEventReminderLead.ONE_HOUR)

        clock.currentInstant = checkNotNull(scheduler.scheduledAt)
        val posted = remind()

        assertEquals(listOf<SkyEvent>(eclipse), posted)
        assertEquals(listOf<SkyEvent>(eclipse), notifier.notified)
        assertEquals(setOf(perseids.id), reminders.current.keys)
        assertEquals(perseids.start.minus(Duration.ofHours(1)), scheduler.scheduledAt)

        // Running again (a duplicate wake-up) posts nothing new.
        assertTrue(remind().isEmpty())
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `a late wake-up still posts for an event that only just started, and drops older ones`() = scope.runTest {
        val perseids = TestSkyEvents.perseids()
        location.setLocation(TestSky.location(coordinate = ZARAGOZA))
        reminders.setReminder(SkyEventReminder(eclipse.id, SkyEventReminderLead.ONE_DAY))
        reminders.setReminder(SkyEventReminder(perseids.id, SkyEventReminderLead.ONE_HOUR))

        // The phone was off a day ahead: it comes back 20 minutes into the eclipse.
        clock.currentInstant = checkNotNull(eclipse.local).partialStart.plus(Duration.ofMinutes(20))
        remind()

        assertEquals(listOf<SkyEvent>(eclipse), notifier.notified)
        assertEquals(setOf(perseids.id), reminders.current.keys)

        // Off again until well into the Perseids' night: too late to be worth a reminder.
        clock.currentInstant = perseids.start.plus(Duration.ofHours(2))
        remind()
        assertEquals(1, notifier.notified.size)
        assertTrue(reminders.current.isEmpty())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `reminders for events that no longer exist are dropped`() = scope.runTest {
        reminders.setReminder(SkyEventReminder("lunar-eclipse:2026-08-29", SkyEventReminderLead.ONE_HOUR))
        reminders.setReminder(SkyEventReminder("not an id", SkyEventReminderLead.ONE_HOUR))

        reschedule()

        assertTrue(reminders.current.isEmpty())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `upcoming events skip quarter Moons and past events`() = scope.runTest {
        clock.currentInstant = Instant.parse("2026-08-12T18:00:00Z")
        val upcoming = ObserveUpcomingSkyEventsUseCase(calendar, location, clock, dispatcher)

        upcoming(limit = 3).test {
            val events = awaitItem()
            assertEquals(
                listOf(
                    TestSkyEvents.perseids().id,
                    TestSkyEvents.venusElongation().id,
                    TestSkyEvents.partialLunarEclipse().id,
                ),
                events.map { it.id },
            )
            assertTrue(events.none { it is MoonPhaseEvent && it.quarter == MoonQuarter.FIRST_QUARTER })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the month's events follow the saved place`() = scope.runTest {
        val observe = ObserveSkyEventsUseCase(calendar, location, dispatcher)

        observe(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")).test {
            assertNull(awaitItem().filterIsInstance<SolarEclipse>().single().local)
            location.setLocation(TestSky.location(coordinate = ZARAGOZA))
            assertEquals(eclipse.local, awaitItem().filterIsInstance<SolarEclipse>().single().local)
            location.clearLocation()
            awaitItem()
            assertEquals(listOf(TestSky.BUCHAREST, ZARAGOZA, null), calendar.observers)
        }
    }

    @Test
    fun `finding an event by id`() = scope.runTest {
        val find = FindSkyEventUseCase(calendar, location, dispatcher)

        assertEquals(TestSkyEvents.perseids(), find(TestSkyEvents.perseids().id))
        assertNull(find("meteors-perseids:2027-08-13"))
    }

    private companion object {
        val ZARAGOZA = GeoCoordinate(latitude = 41.65, longitude = -0.88)
    }
}
