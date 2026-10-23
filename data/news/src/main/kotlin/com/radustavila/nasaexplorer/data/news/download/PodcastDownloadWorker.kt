package com.radustavila.nasaexplorer.data.news.download

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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration

/**
 * Downloads one episode (see [PodcastDownloader]) once there is a network, retrying transient
 * failures with exponential backoff up to [MAX_ATTEMPTS] runs.
 */
@HiltWorker
class PodcastDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val downloader: PodcastDownloader,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        return when (downloader.download(episodeId)) {
            PodcastDownloadOutcome.SAVED, PodcastDownloadOutcome.NOT_NEEDED -> Result.success()
            PodcastDownloadOutcome.FAILED -> Result.failure()
            PodcastDownloadOutcome.RETRY_LATER -> if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                downloader.giveUp(episodeId)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val TAG = "podcast-download"
        const val MAX_ATTEMPTS = 5

        private val INITIAL_BACKOFF: Duration = Duration.ofSeconds(30)

        fun request(episodeId: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<PodcastDownloadWorker>()
                .setInputData(workDataOf(KEY_EPISODE_ID to episodeId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .addTag(TAG)
                .build()

        fun uniqueWorkName(episodeId: String): String = "podcast-download/$episodeId"
    }
}
