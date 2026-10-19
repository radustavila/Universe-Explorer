package com.radustavila.nasaexplorer.data.nasa.sync

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
import com.radustavila.nasaexplorer.core.domain.usecase.AnnounceLatestApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ApplyDailyWallpaperUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.CacheOnThisDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeApodsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SynchronizeUpcomingLaunchesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Background synchronisation.
 *
 * The worker owns no business logic: it delegates to [SynchronizeApodsUseCase] and only decides how
 * to report the outcome to WorkManager. Retryable failures (offline, timeout, 5xx, rate limiting)
 * become `Result.retry()` so the exponential backoff policy applies; permanent failures do not burn
 * retries. A successful sync is followed by the work that depends on fresh data:
 * [AnnounceLatestApodUseCase] refreshes the home screen widget and posts the new-entry notification,
 * [ApplyDailyWallpaperUseCase] updates the opted-in wallpaper, and [CacheOnThisDayUseCase] downloads
 * the "On this day" entries. Those are best effort: their failures never turn a successful sync into
 * a failed or retried one, and the next sync simply tries again.
 *
 * The upcoming-launches cache rides along with every run through
 * [SynchronizeUpcomingLaunchesUseCase]. It is throttled on its own and never affects the result: a
 * retry for a launches failure would only spend more of Launch Library 2's small hourly budget.
 */
@HiltWorker
class ApodSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val synchronizeApods: SynchronizeApodsUseCase,
    private val announceLatestApod: AnnounceLatestApodUseCase,
    private val synchronizeUpcomingLaunches: SynchronizeUpcomingLaunchesUseCase,
    private val applyDailyWallpaper: ApplyDailyWallpaperUseCase,
    private val cacheOnThisDay: CacheOnThisDayUseCase,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val result = synchronizeApods()
        synchronizeUpcomingLaunches()

        return when (result) {
            is DataResult.Success -> {
                announceLatestApod()
                applyDailyWallpaper()
                cacheOnThisDay()
                Result.success(workDataOf(KEY_SYNCED_COUNT to result.data))
            }

            is DataResult.Failure -> if (result.error.isRetryable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(SyncErrorCodec.encode(result.error))
            }
        }
    }

    companion object {
        const val KEY_SYNCED_COUNT = "synced_count"

        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L
        private val PERIODIC_INTERVAL: Duration = Duration.ofHours(12)

        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun oneTimeRequest() = OneTimeWorkRequestBuilder<ApodSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        /**
         * The initial delay matters: WorkManager runs a periodic request as soon as it is
         * registered, which would duplicate the sync every app start performs anyway.
         */
        fun periodicRequest() = PeriodicWorkRequestBuilder<ApodSyncWorker>(PERIODIC_INTERVAL)
            .setInitialDelay(PERIODIC_INTERVAL)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
