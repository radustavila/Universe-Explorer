package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.model.PodcastShow
import kotlinx.coroutines.flow.Flow

/**
 * NASA's podcasts (F21), offline-first like the news they sit beside: the episode lists come from
 * the local cache, [refresh] writes one show into it. Listening progress and downloads are kept per
 * episode on the device.
 */
interface PodcastRepository {

    /** Every cached episode of every show, newest first, with its progress and download. */
    fun observeEpisodes(): Flow<List<PodcastEpisode>>

    fun observeEpisode(id: String): Flow<PodcastEpisode?>

    /**
     * Downloads [show]'s feed and replaces its cached episodes; favourite and downloaded ones stay.
     * A feed that cannot be read leaves the last good copy in place.
     *
     * @return how many episodes the feed listed.
     */
    suspend fun refresh(show: PodcastShow): DataResult<Int>

    /** Remembers where the user is in [episodeId], so it resumes there. */
    suspend fun saveProgress(episodeId: String, progress: PodcastProgress): DataResult<Unit>

    /** Queues a download of the episode's audio for offline listening; runs once online. */
    suspend fun requestDownload(episodeId: String): DataResult<Unit>

    /** Cancels a queued download, or deletes the downloaded file. */
    suspend fun removeDownload(episodeId: String): DataResult<Unit>

    /** The downloaded file's absolute path, if the episode is downloaded and the file is there. */
    suspend fun downloadedAudioPath(episodeId: String): String?
}
