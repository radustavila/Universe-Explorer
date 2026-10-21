package com.radustavila.nasaexplorer.core.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration

/**
 * Keeps one favourite's image offline (see [FavoriteImageDownloader]).
 *
 * Runs only with a network connection and retries transient failures with exponential backoff, up
 * to [MAX_ATTEMPTS] runs; a permanent failure or an item with nothing to keep ends the work at once.
 * The outcome is in the output data under [KEY_OUTCOME].
 */
@HiltWorker
class FavoriteImageDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val downloader: FavoriteImageDownloader,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(KEY_SOURCE)?.let(FavoriteSource::fromKey)
        val itemId = inputData.getString(KEY_ITEM_ID)
        if (source == null || itemId == null) return Result.failure()

        val outcome = downloader.download(source, itemId)
        val output = workDataOf(KEY_OUTCOME to outcome.name)
        return when (outcome) {
            FavoriteImageDownload.SAVED,
            FavoriteImageDownload.ALREADY_SAVED,
            FavoriteImageDownload.NOT_NEEDED,
            -> Result.success(output)

            FavoriteImageDownload.RETRY_LATER ->
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure(output)

            FavoriteImageDownload.FAILED -> Result.failure(output)
        }
    }

    companion object {
        const val KEY_SOURCE = "source"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_OUTCOME = "outcome"
        const val TAG = "favorite-image-download"
        const val MAX_ATTEMPTS = 6

        private val INITIAL_BACKOFF: Duration = Duration.ofSeconds(30)

        fun request(source: FavoriteSource, itemId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<FavoriteImageDownloadWorker>()
                .setInputData(workDataOf(KEY_SOURCE to source.key, KEY_ITEM_ID to itemId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .addTag(TAG)
                .build()

        /** One unique work per favourite, so saving twice never downloads twice. */
        fun uniqueWorkName(source: FavoriteSource, itemId: String): String = "favorite-image/${source.key}/$itemId"
    }
}
