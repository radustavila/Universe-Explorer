package com.radustavila.nasaexplorer.data.quiz.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.domain.quiz.QuizClock
import com.radustavila.nasaexplorer.core.domain.quiz.QuizReminderScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfDailyQuizUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes up at reminder time, reminds of today's quiz if it is not done, and arms tomorrow's. */
@HiltWorker
class QuizReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val remindOfDailyQuiz: RemindOfDailyQuizUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        remindOfDailyQuiz()
        return Result.success()
    }
}

/**
 * One unique delayed job; re-arming replaces it. No constraints: the quiz is offline. A job pending
 * at a reboot runs once the phone is back up (WorkManager keeps it).
 */
@Singleton
class WorkManagerQuizReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: QuizClock,
) : QuizReminderScheduler {

    override fun scheduleAt(at: Instant) {
        val delay = Duration.between(clock.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<QuizReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "quiz-reminder"
    }
}
