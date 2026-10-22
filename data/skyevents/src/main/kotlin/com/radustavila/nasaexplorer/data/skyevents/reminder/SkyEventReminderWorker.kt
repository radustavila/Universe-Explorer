package com.radustavila.nasaexplorer.data.skyevents.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RemindOfSkyEventsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Wakes up when a sky event reminder is due, posts it and arms the next wake-up. */
@HiltWorker
class SkyEventReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val remindOfSkyEvents: RemindOfSkyEventsUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        remindOfSkyEvents()
        return Result.success()
    }
}

/**
 * One unique delayed job; re-arming replaces it. No constraints: events are computed offline. A
 * job pending at a reboot runs once the phone is back up (WorkManager keeps it), late if need be.
 */
@Singleton
class WorkManagerSkyEventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : SkyEventReminderScheduler {

    override fun scheduleAt(at: Instant) {
        val delay = Duration.between(timeProvider.now(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<SkyEventReminderWorker>()
            .setInitialDelay(delay)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }

    companion object {
        const val UNIQUE_WORK = "sky-event-reminder"
    }
}
