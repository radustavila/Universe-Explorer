package com.radustavila.nasaexplorer.data.mars.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeMarsPhotosUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Refreshes the rover photos. Like `ApodSyncWorker` it owns no logic: it delegates to
 * [SynchronizeMarsPhotosUseCase] (which skips while the photos are fresh and records the outcome)
 * and maps the outcome to WorkManager's retry semantics.
 */
@HiltWorker
class MarsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeMarsPhotos: SynchronizeMarsPhotosUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val outcome = synchronizeMarsPhotos(force = inputData.getBoolean(KEY_FORCE, false))
        val result = (outcome as? SourceRefreshResult.Completed)?.result
            ?: return Result.success()
        return when (result) {
            is DataResult.Success -> Result.success(workDataOf(KEY_STORED_COUNT to result.data))
            is DataResult.Failure -> if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_STORED_COUNT = "stored_count"

        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 60L
        private val PERIODIC_INTERVAL = SynchronizeMarsPhotosUseCase.REFRESH_INTERVAL

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(force: Boolean) = OneTimeWorkRequestBuilder<MarsSyncWorker>()
            .setInputData(workDataOf(KEY_FORCE to force))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Delayed by one interval: the rail requests the first download when it appears. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<MarsSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
