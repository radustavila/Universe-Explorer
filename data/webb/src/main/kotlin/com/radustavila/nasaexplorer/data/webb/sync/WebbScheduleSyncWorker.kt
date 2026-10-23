package com.radustavila.nasaexplorer.data.webb.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshWebbScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes Webb's schedule once a day in the background, as STScI's terms ask, so the card and the
 * schedule screen work offline from the last daily refresh. The interval check lives in
 * [RefreshWebbScheduleUseCase], so a run right after the screen refreshed costs nothing. Retryable
 * failures use WorkManager's backoff; others wait for the next day.
 */
@HiltWorker
class WebbScheduleSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val refreshSchedule: RefreshWebbScheduleUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = when (val refresh = refreshSchedule()) {
        is SourceRefreshResult.Skipped -> Result.success()
        is SourceRefreshResult.Completed -> when (val result = refresh.result) {
            is DataResult.Success -> Result.success(workDataOf(KEY_VISIT_COUNT to result.data))
            is DataResult.Failure ->
                if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_VISIT_COUNT = "visit_count"

        private const val MAX_ATTEMPTS = 3
        private val PERIODIC_INTERVAL: Duration = Duration.ofDays(1)
        private val BACKOFF: Duration = Duration.ofMinutes(5)

        fun periodicRequest() = PeriodicWorkRequestBuilder<WebbScheduleSyncWorker>(PERIODIC_INTERVAL)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
    }
}

/** Registers the daily [WebbScheduleSyncWorker]. Idempotent, so it is safe on every app start. */
@Singleton
class WebbScheduleSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensurePeriodicSync() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            WebbScheduleSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val PERIODIC_SYNC_WORK = "webb-schedule-sync-periodic"
    }
}
