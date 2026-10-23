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
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEarthquakesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the open-events and earthquake caches fresh in the background, so the maps and the Today
 * card work offline and the strong-quake alert sees new quakes.
 *
 * Separate from the APOD sync because it runs on its own clock (every few hours rather than twice a
 * day). The interval checks live in [RefreshEarthEventsUseCase] and [RefreshEarthquakesUseCase], so
 * a run right after the events screen refreshed costs nothing. Retryable failures use WorkManager's
 * backoff; others wait for the next period.
 */
@HiltWorker
class EarthEventsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val refreshEarthEvents: RefreshEarthEventsUseCase,
    private val refreshEarthquakes: RefreshEarthquakesUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val events = refreshEarthEvents()
        val quakes = refreshEarthquakes()
        val retry = listOf(events, quakes).any { refresh ->
            val result = (refresh as? SourceRefreshResult.Completed)?.result
            result is DataResult.Failure && result.error.isRetryable
        }
        if (retry && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        val counts = listOfNotNull(
            events.countOrNull()?.let { KEY_EVENT_COUNT to it },
            quakes.countOrNull()?.let { KEY_EARTHQUAKE_COUNT to it },
        )
        return Result.success(workDataOf(*counts.toTypedArray()))
    }

    private fun SourceRefreshResult<Int>.countOrNull(): Int? =
        ((this as? SourceRefreshResult.Completed)?.result as? DataResult.Success)?.data

    companion object {
        const val KEY_EVENT_COUNT = "event_count"
        const val KEY_EARTHQUAKE_COUNT = "earthquake_count"

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
