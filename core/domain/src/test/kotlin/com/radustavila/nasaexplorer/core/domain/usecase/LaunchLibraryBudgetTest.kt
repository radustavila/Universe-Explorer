package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launch Library 2 allows 15 anonymous requests an hour per IP, shared by the next-launches card,
 * the crew list and the Launches screen (F27). A heavy day of use must stay inside it with room to
 * spare, without the HTTP rate limiter having to refuse anything.
 */
class LaunchLibraryBudgetTest {

    private val start: Instant = TestLaunches.NOW.minus(Duration.ofHours(10))
    private val clock = FakeTimeProvider(currentInstant = start)
    private val metadata = FakeSyncMetadataRepository()
    private val network = FakeNetworkMonitor()
    private val launches = FakeLaunchRepository().apply {
        refreshResult = DataResult.Success(30)
        refreshPastResult = DataResult.Success(20)
    }
    private val people = FakePeopleInSpaceRepository().apply { refreshResult = DataResult.Success(12) }
    private val reminders = FakeLaunchReminderRepository()
    private val scheduler = FakeLaunchReminderScheduler()
    private val refreshSource = RefreshSourceUseCase(metadata, clock)
    private val reschedule = RescheduleLaunchRemindersUseCase(launches, reminders, scheduler, clock)

    /** The sync worker's part: app start, pull-to-refresh on Today and the 12-hourly job. */
    private val synchronizeUpcoming =
        SynchronizeUpcomingLaunchesUseCase(launches, network, refreshSource, FakeHomeWidgetUpdater(), reschedule)
    private val refreshPast = RefreshPastLaunchesUseCase(launches, network, refreshSource, reschedule)
    private val refreshPeople = RefreshPeopleInSpaceUseCase(people, metadata, clock, refreshSource)
    private val setReminder = SetLaunchReminderUseCase(reminders, reschedule)
    private val remind = RemindOfLaunchesUseCase(
        launchRepository = launches,
        reminderRepository = reminders,
        notifier = FakeLaunchReminderNotifier(),
        networkMonitor = network,
        syncMetadataRepository = metadata,
        rescheduleReminders = reschedule,
        timeProvider = clock,
    )

    @Test
    fun `a heavy day with the card, the crew list, the screen and four reminders stays under 15 calls an hour`() =
        runTest {
            // Four reminded launches late in the evening, 20 minutes apart, so their re-checks bunch up
            // with the last hour of use.
            val reminded = (0 until 4).map { index ->
                TestLaunches.launch(
                    id = "reminded-$index",
                    net = start.plus(Duration.ofMinutes(22L * 60 + 30 + 20L * index)),
                )
            }
            launches.setLaunches(reminded + TestLaunches.upcoming(10))
            reminded.forEach { setReminder(it.id, enabled = true) }

            val callTimes = mutableListOf<Instant>()
            val people = this@LaunchLibraryBudgetTest.people
            var lastCount = 0
            fun recordCalls() {
                val count = launches.callCount + people.refreshCallCount
                repeat(count - lastCount) { callTimes += clock.currentInstant }
                lastCount = count
            }

            // A minute at a time over a day: the app opened every 10 minutes from 07:00 to 23:00,
            // each time with Today's pull-to-refresh, the Launches screen and Sky tonight's
            // pull-to-refresh; the reminder job wherever it was armed.
            repeat(MINUTES_PER_DAY) { minute ->
                clock.currentInstant = start.plus(Duration.ofMinutes(minute.toLong()))
                val hour = minute / MINUTES_PER_HOUR
                if (hour in AWAKE_HOURS && minute % APP_OPEN_EVERY_MINUTES == 0) {
                    synchronizeUpcoming()
                    synchronizeUpcoming()
                    refreshPast()
                    refreshPeople(force = true)
                }
                if (minute % PERIODIC_SYNC_EVERY_MINUTES == 0) synchronizeUpcoming()
                val wakeUp = scheduler.scheduledAt
                if (wakeUp != null && !wakeUp.isAfter(clock.currentInstant)) remind()
                recordCalls()
            }

            val busiestHour = callTimes.maxOf { from ->
                callTimes.count { it >= from && it < from.plus(Duration.ofHours(1)) }
            }
            assertTrue("busiest hour made $busiestHour calls", busiestHour <= HOURLY_BUDGET - HEADROOM)
            // Hourly list, 6-hourly past list, at most one re-check per reminder (none when the list
            // is fresh anyway).
            assertTrue(launches.refreshedLaunchIds.isNotEmpty())
            assertEquals(launches.refreshedLaunchIds.distinct(), launches.refreshedLaunchIds)
            assertTrue(launches.refreshCallCount <= 24)
            assertTrue(launches.refreshPastCallCount <= 4)
        }

    private companion object {
        const val HOURLY_BUDGET = 15

        /** Calls left for anything this test does not model (a second device on the same Wi-Fi). */
        const val HEADROOM = 5
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val APP_OPEN_EVERY_MINUTES = 10
        const val PERIODIC_SYNC_EVERY_MINUTES = 12 * 60
        val AWAKE_HOURS = 7..22
    }
}
