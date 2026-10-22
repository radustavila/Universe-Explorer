package com.radustavila.nasaexplorer.data.forecast.alerts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.usecase.RunStargazingAlertCheckUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * The clear-night alert's background check: refreshes the forecast for the saved place (MET
 * Norway's `Expires` decides whether a request is sent) and announces tonight if it is clear.
 * Registered only while the alert is on, so the app never fetches in the background otherwise.
 */
@HiltWorker
class StargazingAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val runCheck: RunStargazingAlertCheckUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val result = (runCheck() as? SourceRefreshResult.Completed)?.result ?: return Result.success()
        return if (result is DataResult.Failure && result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MINUTES = 5L

        /**
         * MET Norway's terms allow an alert worker every 10 minutes; cloud forecasts for tonight
         * change slowly, and every 3 hours still catches each day's afternoon and evening runs.
         * WorkManager starts it from whenever the alert was switched on, so devices do not bunch
         * their requests on the hour.
         */
        val PERIODIC_INTERVAL: Duration = Duration.ofHours(3)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun periodicRequest() = PeriodicWorkRequestBuilder<StargazingAlertWorker>(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
    }
}
