package com.radustavila.nasaexplorer.data.launches.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfLaunchesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes up for a launch reminder's re-check or notification, does what is due and arms the next wake-up. */
@HiltWorker
class LaunchReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val remindOfLaunches: RemindOfLaunchesUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        remindOfLaunches()
        return Result.success()
    }
}

/**
 * One unique delayed job; re-arming replaces it. No network constraint: the notification is posted
 * from the cache, and the re-check simply does not happen offline. A job pending at a reboot runs
 * once the phone is back up (WorkManager keeps it).
 */
@Singleton
class WorkManagerLaunchReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : LaunchReminderScheduler {

    override fun scheduleAt(at: Instant) {
        val delay = Duration.between(timeProvider.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<LaunchReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "launch-reminder"
    }
}
