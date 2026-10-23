package com.radustavila.nasaexplorer.data.comets.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshCometsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the comets once a day in the background, so the comets screen and Sky tonight's card
 * work offline from the last daily refresh. The interval check lives in [RefreshCometsUseCase], so
 * a run right after the screen refreshed costs nothing. Retryable failures use WorkManager's
 * backoff; others wait for the next day.
 */
@HiltWorker
class CometsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val refreshComets: RefreshCometsUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = when (val refresh = refreshComets()) {
        is SourceRefreshResult.Skipped -> Result.success()
        is SourceRefreshResult.Completed -> when (val result = refresh.result) {
            is DataResult.Success -> Result.success(workDataOf(KEY_COMET_COUNT to result.data))
            is DataResult.Failure ->
                if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_COMET_COUNT = "comet_count"

        private const val MAX_ATTEMPTS = 3
        private val PERIODIC_INTERVAL: Duration = Duration.ofDays(1)
        private val BACKOFF: Duration = Duration.ofMinutes(5)

        fun periodicRequest() = PeriodicWorkRequestBuilder<CometsSyncWorker>(PERIODIC_INTERVAL)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
            .build()
    }
}

/** Registers the daily [CometsSyncWorker]. Idempotent, so it is safe on every app start. */
@Singleton
class CometsSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensurePeriodicSync() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            CometsSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val PERIODIC_SYNC_WORK = "comets-sync-periodic"
    }
}
