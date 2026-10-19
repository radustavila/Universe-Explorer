package com.radustavila.nasaexplorer.data.smallbodies.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeSmallBodiesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Refreshes close approaches, the Sentry list and fireballs. Delegates to
 * [SynchronizeSmallBodiesUseCase] (which skips while fresh) and maps the outcome to WorkManager's
 * retry semantics.
 */
@HiltWorker
class SmallBodiesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeSmallBodies: SynchronizeSmallBodiesUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val outcome = synchronizeSmallBodies(force = inputData.getBoolean(KEY_FORCE, false))
        val result = (outcome as? SourceRefreshResult.Completed)?.result ?: return Result.success()
        return when (result) {
            is DataResult.Success -> Result.success()
            is DataResult.Failure -> if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_FORCE = "force"

        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L
        val PERIODIC_INTERVAL: Duration = Duration.ofDays(1)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(force: Boolean) = OneTimeWorkRequestBuilder<SmallBodiesSyncWorker>()
            .setInputData(workDataOf(KEY_FORCE to force))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /** Delayed by one interval: whoever registers it has just requested a refresh. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<SmallBodiesSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
