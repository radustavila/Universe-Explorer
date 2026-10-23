package com.radustavila.nasaexplorer.data.quiz.reminder

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
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfDailyQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetQuizReminderEnabledUseCase
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizClock
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizProgressRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizReminderNotifier
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The reminder path on a test WorkManager: turning it on enqueues one job for 18:00 local time,
 * and when it fires the worker posts the reminder and arms the next day's.
 */
@RunWith(RobolectricTestRunner::class)
class QuizReminderWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeQuizClock(instant = Instant.parse("2026-10-23T10:00:00Z"))
    private val progress = FakeQuizProgressRepository(
        QuizProgress(lastDailyDate = LocalDate.of(2026, 10, 22), streak = 2),
    )
    private val notifier = FakeQuizReminderNotifier()
    private val scheduler = WorkManagerQuizReminderScheduler(context, clock)
    private val remind = RemindOfDailyQuizUseCase(progress, scheduler, notifier, clock)

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
                        ): ListenableWorker = QuizReminderWorker(appContext, workerParameters, remind)
                    },
                )
                .build(),
        )
    }

    @Test
    fun `the reminder fires at 18 00 and arms tomorrow's`() = runBlocking {
        SetQuizReminderEnabledUseCase(progress, scheduler, clock)(true)

        val job = uniqueWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        // 18:00 in Bucharest is 15:00 UTC in October: five hours from 10:00 UTC.
        assertEquals(Duration.ofHours(5).toMillis(), job.initialDelayMillis)

        clock.instant = Instant.parse("2026-10-23T15:00:00Z")
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(job.id)

        assertEquals(listOf(2), notifier.notified)
        val pending = uniqueWork().filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(Duration.ofDays(1).toMillis(), pending.single().initialDelayMillis)
    }

    @Test
    fun `turning it off cancels the pending job`() = runBlocking {
        val set = SetQuizReminderEnabledUseCase(progress, scheduler, clock)
        set(true)
        set(false)

        assertTrue(uniqueWork().none { it.state == WorkInfo.State.ENQUEUED })
    }

    private fun uniqueWork(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(WorkManagerQuizReminderScheduler.UNIQUE_WORK)
        .get()
}
