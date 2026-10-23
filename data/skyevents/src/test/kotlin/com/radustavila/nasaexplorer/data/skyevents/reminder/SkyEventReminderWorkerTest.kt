package com.radustavila.nasaexplorer.data.skyevents.reminder

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
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometMagnitudeLimitUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCometPerihelionEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfSkyEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleSkyEventRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyEventReminderUseCase
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometDebugSettingsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventCalendar
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyEventReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole reminder path on a test WorkManager: choosing a reminder enqueues one delayed job with
 * the lead time worked in, and when the delay is met the worker posts the reminder.
 */
@RunWith(RobolectricTestRunner::class)
class SkyEventReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-08-10T12:00:00Z"))
    private val calendar = FakeSkyEventCalendar(TestSkyEvents.august2026())
    private val location = FakeObserverLocationRepository(TestSky.location(coordinate = GeoCoordinate(41.65, -0.88)))
    private val reminders = FakeSkyEventReminderRepository()
    private val notifier = FakeSkyEventNotifier()
    private val scheduler = WorkManagerSkyEventReminderScheduler(context, clock)
    private val cometEvents = ObserveCometPerihelionEventsUseCase(
        FakeCometRepository(),
        ObserveCometMagnitudeLimitUseCase(FakeCometDebugSettingsRepository()),
    )
    private val reschedule = RescheduleSkyEventRemindersUseCase(
        reminders,
        calendar,
        location,
        cometEvents,
        scheduler,
        clock,
        Dispatchers.Unconfined,
    )
    private val remind = RemindOfSkyEventsUseCase(
        reminders,
        calendar,
        location,
        cometEvents,
        notifier,
        reschedule,
        clock,
        Dispatchers.Unconfined,
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
                        ): ListenableWorker =
                            SkyEventReminderWorker(appContext, workerParameters, remind)
                    },
                )
                .build(),
        )
    }

    @Test
    fun `a chosen reminder fires at its lead time through the worker`() = runBlocking {
        val eclipse = TestSkyEvents.totalSolarEclipse()
        SetSkyEventReminderUseCase(reminders, reschedule)(eclipse.id, SkyEventReminderLead.ONE_HOUR)

        val job = uniqueWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        val expectedDelay = Duration.between(clock.now(), eclipse.start.minus(Duration.ofHours(1)))
        assertEquals(expectedDelay.toMillis(), job.initialDelayMillis)

        // Time passes, and the delay is met.
        clock.currentInstant = eclipse.start.minus(Duration.ofHours(1))
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(job.id)

        assertEquals(listOf<SkyEvent>(eclipse), notifier.notified)
        assertTrue(reminders.current.isEmpty())
        // Nothing left to remind of: the next wake-up is cancelled.
        val states = uniqueWork()
            .map { it.state }
        assertTrue(states.all { it == WorkInfo.State.SUCCEEDED || it == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `re-arming replaces the pending job`() {
        scheduler.scheduleAt(clock.now().plus(Duration.ofHours(5)))
        scheduler.scheduleAt(clock.now().plus(Duration.ofHours(2)))

        val jobs = uniqueWork()
        val pending = jobs.filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(1, pending.size)
        assertEquals(TimeUnit.HOURS.toMillis(2), pending.single().initialDelayMillis)

        scheduler.cancel()
        assertTrue(
            uniqueWork()
                .none { it.state == WorkInfo.State.ENQUEUED },
        )
    }

    private fun uniqueWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerSkyEventReminderScheduler.UNIQUE_WORK)
        .get()
}
