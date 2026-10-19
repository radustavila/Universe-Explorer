package com.radustavila.nasaexplorer.data.earthevents.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthEventsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the open-events cache fresh in the background, so the map and the Today card work offline.
 *
 * Separate from the APOD sync because it runs on its own clock (every few hours rather than twice a
 * day). The interval check lives in [RefreshEarthEventsUseCase], so a run right after the events
 * screen refreshed costs nothing. Retryable failures use WorkManager's backoff; others wait for the
 * next period.
 */
@HiltWorker
class EarthEventsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val refreshEarthEvents: RefreshEarthEventsUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = when (val refresh = refreshEarthEvents()) {
        is SourceRefreshResult.Skipped -> Result.success()
        is SourceRefreshResult.Completed -> when (val result = refresh.result) {
            is DataResult.Success -> Result.success(workDataOf(KEY_EVENT_COUNT to result.data))
            is DataResult.Failure ->
                if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        const val KEY_EVENT_COUNT = "event_count"

        private const val MAX_ATTEMPTS = 3

        fun periodicRequest() = PeriodicWorkRequestBuilder<EarthEventsSyncWorker>(
            RefreshEarthEventsUseCase.MIN_REFRESH_INTERVAL,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
            .build()
    }
}

/** Registers the periodic [EarthEventsSyncWorker]. Idempotent, so it is safe on every app start. */
@Singleton
class EarthEventsSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensurePeriodicSync() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            EarthEventsSyncWorker.periodicRequest(),
        )
    }

    companion object {
        const val PERIODIC_SYNC_WORK = "earth-events-sync-periodic"
    }
}
