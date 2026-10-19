package com.radustavila.nasaexplorer.data.spaceweather.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeSpaceWeatherUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Refreshes space weather and checks aurora alerts. Like `ApodSyncWorker` it owns no logic: it
 * delegates to [SynchronizeSpaceWeatherUseCase] (which skips fresh sources) and maps the NOAA outcome
 * to WorkManager's retry semantics.
 */
@HiltWorker
class SpaceWeatherSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeSpaceWeather: SynchronizeSpaceWeatherUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val outcome = synchronizeSpaceWeather(
            force = inputData.getBoolean(KEY_FORCE, false),
            includeAuroraMap = inputData.getBoolean(KEY_INCLUDE_AURORA_MAP, false),
        )
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
        const val KEY_INCLUDE_AURORA_MAP = "include_aurora_map"

        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L

        /** Kp comes in 3-hour slots but the forecast and the 1-minute estimate move faster. */
        val PERIODIC_INTERVAL: Duration = Duration.ofMinutes(30)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest(force: Boolean, includeAuroraMap: Boolean) =
            OneTimeWorkRequestBuilder<SpaceWeatherSyncWorker>()
                .setInputData(workDataOf(KEY_FORCE to force, KEY_INCLUDE_AURORA_MAP to includeAuroraMap))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

        /** Delayed by one interval: whoever registers it has just requested a refresh. */
        fun periodicRequest() = PeriodicWorkRequestBuilder<SpaceWeatherSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
