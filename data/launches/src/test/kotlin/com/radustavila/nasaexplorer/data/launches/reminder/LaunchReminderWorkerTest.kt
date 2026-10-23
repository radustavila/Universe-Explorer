package com.radustavila.nasaexplorer.data.launches.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfLaunchesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleLaunchRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetLaunchReminderUseCase
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole reminder path on a test WorkManager: ringing the bell enqueues one delayed job for the
 * re-check, which arms the notification's, which posts it.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeTimeProvider(currentInstant = TestLaunches.NOW)
    private val launch = TestLaunches.launch(net = TestLaunches.NOW.plus(Duration.ofHours(3)))
    private val launches = FakeLaunchRepository().apply {
        setLaunches(listOf(launch))
        refreshedLaunches[launch.id] = launch
    }
    private val reminders = FakeLaunchReminderRepository()
    private val notifier = FakeLaunchReminderNotifier()
    private val scheduler = WorkManagerLaunchReminderScheduler(context, clock)
    private val reschedule = RescheduleLaunchRemindersUseCase(launches, reminders, scheduler, clock)
    private val remind = RemindOfLaunchesUseCase(
        launchRepository = launches,
        reminderRepository = reminders,
        notifier = notifier,
        networkMonitor = FakeNetworkMonitor(),
        syncMetadataRepository = FakeSyncMetadataRepository(),
        rescheduleReminders = reschedule,
        timeProvider = clock,
    )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = LaunchReminderWorker(appContext, workerParameters, remind)
                    },
                )
                .build(),
        )
    }

    @Test
    fun `a rung bell re-checks an hour before and notifies 15 minutes before NET`() = runBlocking {
        SetLaunchReminderUseCase(reminders, reschedule)(launch.id, enabled = true)

        val recheck = pending()
        assertEquals(TimeUnit.HOURS.toMillis(2), recheck.initialDelayMillis)

        clock.currentInstant = launch.net.minus(Duration.ofHours(1))
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(recheck.id)
        assertEquals(listOf(launch.id), launches.refreshedLaunchIds)
        val notification = pending()
        assertEquals(TimeUnit.MINUTES.toMillis(45), notification.initialDelayMillis)

        clock.currentInstant = launch.net.minus(Duration.ofMinutes(15))
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(notification.id)
        assertEquals(listOf(launch), notifier.notified)
        assertTrue(uniqueWork().none { it.state == WorkInfo.State.ENQUEUED })
    }

    private fun pending(): WorkInfo = uniqueWork().single { it.state == WorkInfo.State.ENQUEUED }

    private fun uniqueWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerLaunchReminderScheduler.UNIQUE_WORK)
        .get()
}
