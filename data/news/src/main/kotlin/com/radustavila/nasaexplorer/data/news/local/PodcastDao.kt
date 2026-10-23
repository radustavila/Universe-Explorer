package com.radustavila.nasaexplorer.data.news.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Dao
abstract class PodcastDao {

    @Query(
        """
        SELECT e.*,
            p.position_ms AS progress_position_ms, p.is_finished AS progress_is_finished,
            d.state AS download_state, d.bytes AS download_bytes
        FROM podcast_episode e
        LEFT JOIN podcast_progress p ON p.episode_id = e.id
        LEFT JOIN podcast_download d ON d.episode_id = e.id
        ORDER BY e.published_at DESC, e.id
        """,
    )
    abstract fun observeAll(): Flow<List<PodcastEpisodeWithState>>

    @Query(
        """
        SELECT e.*,
            p.position_ms AS progress_position_ms, p.is_finished AS progress_is_finished,
            d.state AS download_state, d.bytes AS download_bytes
        FROM podcast_episode e
        LEFT JOIN podcast_progress p ON p.episode_id = e.id
        LEFT JOIN podcast_download d ON d.episode_id = e.id
        WHERE e.id = :id
        """,
    )
    abstract fun observeById(id: String): Flow<PodcastEpisodeWithState?>

    @Query("SELECT * FROM podcast_episode WHERE id = :id")
    abstract suspend fun findEpisode(id: String): PodcastEpisodeEntity?

    @Query("DELETE FROM podcast_episode WHERE show = :show AND id NOT IN (:keepIds)")
    protected abstract suspend fun deleteShowExcept(show: String, keepIds: List<String>)

    @Upsert
    protected abstract suspend fun upsertEpisodes(episodes: List<PodcastEpisodeEntity>)

    /**
     * Installs a show's fresh episodes in one transaction: episodes it no longer lists go, except
     * [keepIds] (favourites) and downloaded ones, which must keep opening and playing offline.
     */
    @Transaction
    open suspend fun replaceShow(show: String, episodes: List<PodcastEpisodeEntity>, keepIds: Collection<String>) {
        deleteShowExcept(show, (keepIds + downloadIds() + episodes.map(PodcastEpisodeEntity::id)).distinct())
        upsertEpisodes(episodes)
    }

    @Query(
        """
        DELETE FROM podcast_episode
        WHERE cached_at < :beforeMillis AND id NOT IN (:keepIds)
            AND id NOT IN (SELECT episode_id FROM podcast_download)
        """,
    )
    abstract suspend fun deleteCachedBefore(beforeMillis: Long, keepIds: List<String>): Int

    @Upsert
    abstract suspend fun upsertProgress(progress: PodcastProgressEntity)

    @Query("SELECT * FROM podcast_download WHERE episode_id = :episodeId")
    abstract suspend fun findDownload(episodeId: String): PodcastDownloadEntity?

    @Query("SELECT * FROM podcast_download")
    abstract suspend fun downloads(): List<PodcastDownloadEntity>

    @Query("SELECT episode_id FROM podcast_download")
    protected abstract suspend fun downloadIds(): List<String>

    @Upsert
    abstract suspend fun upsertDownload(download: PodcastDownloadEntity)

    /** Moves a download on only while it is still wanted (its row exists); returns whether it was. */
    @Query(
        """
        UPDATE podcast_download SET state = :state, file_path = :filePath, bytes = :bytes, updated_at = :at
        WHERE episode_id = :episodeId
        """,
    )
    abstract suspend fun updateDownload(episodeId: String, state: String, filePath: String?, bytes: Long, at: Long): Int

    @Query("DELETE FROM podcast_download WHERE episode_id = :episodeId")
    abstract suspend fun deleteDownload(episodeId: String): Int
}
