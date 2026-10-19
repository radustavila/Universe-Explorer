package com.radustavila.nasaexplorer.data.sky.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.IssPassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfIssPassUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes up shortly before a visible ISS pass, posts the reminder and arms the next wake-up. */
@HiltWorker
class IssPassReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val remindOfIssPass: RemindOfIssPassUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        remindOfIssPass()
        return Result.success()
    }
}

/**
 * One unique delayed job; re-arming replaces it. No network constraint: the reminder works from
 * cached elements, and refreshing them on the way is best-effort.
 */
@Singleton
class WorkManagerIssPassReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : IssPassReminderScheduler {

    override fun scheduleAt(at: Instant) {
        val delay = Duration.between(timeProvider.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<IssPassReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "iss-pass-reminder"
    }
}
