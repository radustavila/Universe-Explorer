package com.radustavila.nasaexplorer.data.exoplanets.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeExoplanetsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Refreshes the exoplanet catalogue. Like `ApodSyncWorker` it owns no logic: it delegates to
 * [SynchronizeExoplanetsUseCase] (which skips while the catalogue is fresh) and maps the outcome to
 * WorkManager's retry semantics. The outcome itself is recorded per source by the use case.
 */
@HiltWorker
class ExoplanetSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeExoplanets: SynchronizeExoplanetsUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val outcome = synchronizeExoplanets(force = inputData.getBoolean(KEY_FORCE, false))
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
        private const val BACKOFF_SECONDS = 30L
        private val PERIODIC_INTERVAL = SynchronizeExoplanetsUseCase.REFRESH_INTERVAL

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(force: Boolean) = OneTimeWorkRequestBuilder<ExoplanetSyncWorker>()
            .setInputData(workDataOf(KEY_FORCE to force))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Delayed by one interval: opening the screen already requests the first download. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<ExoplanetSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
