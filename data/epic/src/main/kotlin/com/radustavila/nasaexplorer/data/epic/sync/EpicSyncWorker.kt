package com.radustavila.nasaexplorer.data.epic.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeEpicUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Background synchronisation of the EPIC cache. Like the APOD worker it owns one decision only:
 * retry (transient failure, attempts left) or fail with the reason encoded for observers. Whether a
 * sync is due, and the per-source record of how it went, belong to [SynchronizeEpicUseCase].
 */
@HiltWorker
class EpicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeEpic: SynchronizeEpicUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        // Skipped: synced recently enough, nothing to report.
        val result = (synchronizeEpic() as? SourceRefreshResult.Completed)?.result
            ?: return Result.success(workDataOf(KEY_SYNCED_COUNT to 0))

        return when (result) {
            is DataResult.Success -> Result.success(workDataOf(KEY_SYNCED_COUNT to result.data))

            is DataResult.Failure -> if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(EpicSyncErrorCodec.encode(result.error))
            }
        }
    }

    companion object {
        const val KEY_SYNCED_COUNT = "synced_count"

        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L

        /** EPIC publishes about once a day, usually a day or two behind. */
        private val PERIODIC_INTERVAL: Duration = Duration.ofHours(12)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest() = OneTimeWorkRequestBuilder<EpicSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Delayed by one interval, since opening the Earth screen or Today already syncs. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<EpicSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
