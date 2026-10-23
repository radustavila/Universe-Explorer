package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminder
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A launch reminder fires once, 15 minutes before NET, follows a slipped NET and stays silent on a scrub. */
class LaunchReminderUseCasesTest {

    private val clock = FakeTimeProvider(currentInstant = TestLaunches.NOW)
    private val launches = FakeLaunchRepository()
    private val reminders = FakeLaunchReminderRepository()
    private val scheduler = FakeLaunchReminderScheduler()
    private val notifier = FakeLaunchReminderNotifier()
    private val network = FakeNetworkMonitor()
    private val metadata = FakeSyncMetadataRepository()

    private val reschedule = RescheduleLaunchRemindersUseCase(launches, reminders, scheduler, clock)
    private val setReminder = SetLaunchReminderUseCase(reminders, reschedule)
    private val remind = RemindOfLaunchesUseCase(
        launchRepository = launches,
        reminderRepository = reminders,
        notifier = notifier,
        networkMonitor = network,
        syncMetadataRepository = metadata,
        rescheduleReminders = reschedule,
        timeProvider = clock,
    )

    private val net: Instant = TestLaunches.NOW.plus(Duration.ofHours(3))
    private val launch = TestLaunches.launch(net = net)

    @Test
    fun `ringing the bell arms the re-check an hour before NET`() = runTest {
        launches.setLaunches(listOf(launch))

        setReminder(launch.id, enabled = true)

        assertEquals(setOf(launch.id), reminders.current.keys)
        assertEquals(net.minus(Duration.ofMinutes(60)), scheduler.scheduledAt)
    }

    @Test
    fun `silencing the last bell cancels the wake-up`() = runTest {
        launches.setLaunches(listOf(launch))
        setReminder(launch.id, enabled = true)

        setReminder(launch.id, enabled = false)

        assertTrue(reminders.current.isEmpty())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `the re-check calls Launch Library once and then arms the notification`() = runTest {
        launches.setLaunches(listOf(launch))
        launches.refreshedLaunches[launch.id] = launch
        setReminder(launch.id, enabled = true)

        clock.currentInstant = net.minus(Duration.ofMinutes(60))
        remind()

        assertEquals(listOf(launch.id), launches.refreshedLaunchIds)
        assertEquals(net, reminders.current.getValue(launch.id).checkedNet)
        assertEquals(net.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `the re-check is skipped when the list was refreshed in the last half hour`() = runTest {
        launches.setLaunches(listOf(launch))
        setReminder(launch.id, enabled = true)
        clock.currentInstant = net.minus(Duration.ofMinutes(60))
        metadata.recordSyncSuccess(SyncSource.LAUNCHES, at = clock.currentInstant.minus(Duration.ofMinutes(10)))

        remind()

        assertTrue(launches.refreshedLaunchIds.isEmpty())
        assertEquals(net.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)
    }

    @Test
    fun `the notification fires once, 15 minutes before NET, offline from the cache`() = runTest {
        launches.setLaunches(listOf(launch))
        setReminder(launch.id, enabled = true)
        network.setOnline(false)

        clock.currentInstant = net.minus(Duration.ofMinutes(60))
        remind()
        clock.currentInstant = net.minus(Duration.ofMinutes(15))
        remind()
        clock.currentInstant = net.minus(Duration.ofMinutes(10))
        remind()

        assertEquals(listOf(launch), notifier.notified)
        assertTrue(launches.refreshedLaunchIds.isEmpty())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun `a NET that slips at the re-check moves the notification with it`() = runTest {
        launches.setLaunches(listOf(launch))
        val slipped = launch.copy(net = net.plus(Duration.ofMinutes(45)))
        launches.refreshedLaunches[launch.id] = slipped
        setReminder(launch.id, enabled = true)

        clock.currentInstant = net.minus(Duration.ofMinutes(60))
        remind()
        clock.currentInstant = net.minus(Duration.ofMinutes(15))
        remind()

        assertTrue(notifier.notified.isEmpty())
        assertEquals(slipped.net.minus(Duration.ofMinutes(15)), scheduler.scheduledAt)
        assertEquals(1, launches.refreshedLaunchIds.size)
    }

    @Test
    fun `a scrub to a hold silences the reminder until a refresh brings a firm NET`() = runTest {
        launches.setLaunches(listOf(launch))
        launches.refreshedLaunches[launch.id] = launch.copy(status = LaunchStatus.HOLD)
        setReminder(launch.id, enabled = true)

        clock.currentInstant = net.minus(Duration.ofMinutes(60))
        remind()
        clock.currentInstant = net.minus(Duration.ofMinutes(15))
        remind()

        assertTrue(notifier.notified.isEmpty())
        assertNull(scheduler.scheduledAt)
        assertEquals(setOf(launch.id), reminders.current.keys)

        // The next refresh brings the new date: the reminder arms again for it.
        val tomorrow = net.plus(Duration.ofDays(1))
        launches.setLaunches(listOf(launch.copy(net = tomorrow)))
        reschedule()
        assertEquals(tomorrow.minus(Duration.ofMinutes(60)), scheduler.scheduledAt)
    }

    @Test
    fun `a slip after the notification posts it again for the new NET`() = runTest {
        launches.setLaunches(listOf(launch))
        setReminder(launch.id, enabled = true)
        clock.currentInstant = net.minus(Duration.ofMinutes(15))
        remind()

        val tomorrow = net.plus(Duration.ofDays(1))
        launches.setLaunches(listOf(launch.copy(net = tomorrow)))
        reschedule()
        clock.currentInstant = tomorrow.minus(Duration.ofMinutes(15))
        remind()

        assertEquals(listOf(net, tomorrow), notifier.notified.map { it.net })
    }

    @Test
    fun `a launch without a firm time waits without a wake-up`() = runTest {
        launches.setLaunches(listOf(launch.copy(status = LaunchStatus.TBD, netPrecision = NetPrecision.MONTH)))

        setReminder(launch.id, enabled = true)

        assertNull(scheduler.scheduledAt)
        assertEquals(setOf(launch.id), reminders.current.keys)
    }

    @Test
    fun `reminders of flown or vanished launches are dropped, but not while the cache is empty`() = runTest {
        reminders.setReminder(LaunchReminder("gone"))
        reschedule()
        assertEquals(setOf("gone"), reminders.current.keys)

        launches.setLaunches(listOf(launch))
        launches.setPastLaunches(listOf(TestLaunches.launch(id = "flown", status = LaunchStatus.SUCCESS)))
        reminders.setReminder(LaunchReminder("flown"))
        reminders.setReminder(LaunchReminder(launch.id))
        reschedule()

        assertEquals(setOf(launch.id), reminders.current.keys)
    }
}
