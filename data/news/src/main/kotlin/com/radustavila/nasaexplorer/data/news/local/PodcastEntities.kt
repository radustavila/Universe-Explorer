package com.radustavila.nasaexplorer.data.news.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One episode of one show, as its feed last listed it. Each refresh replaces its show's rows. */
@Entity(
    tableName = "podcast_episode",
    indices = [Index("published_at"), Index("show")],
)
data class PodcastEpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** `PodcastShow.key`. */
    @ColumnInfo(name = "show")
    val show: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "published_at")
    val publishedAtMillis: Long,
    @ColumnInfo(name = "audio_url")
    val audioUrl: String,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long?,
    @ColumnInfo(name = "summary")
    val summary: String?,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "page_url")
    val pageUrl: String?,
    /** When the feed last listed it, for eviction. */
    @ColumnInfo(name = "cached_at")
    val cachedAtMillis: Long,
)

/**
 * Where the user is in an episode. Kept apart from the episode so a refresh, which replaces the
 * episodes, never resets it; the rows are tiny and outlive their episodes.
 */
@Entity(tableName = "podcast_progress")
data class PodcastProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    @ColumnInfo(name = "position_ms")
    val positionMillis: Long,
    @ColumnInfo(name = "is_finished")
    val isFinished: Boolean,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long,
)

/** An episode the user asked to keep offline, and how far that got. */
@Entity(tableName = "podcast_download")
data class PodcastDownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "episode_id")
    val episodeId: String,
    /** `PodcastDownloadState.name`. */
    @ColumnInfo(name = "state")
    val state: String,
    /** Relative to the downloads folder (see `PodcastFiles`), once downloaded. */
    @ColumnInfo(name = "file_path")
    val filePath: String?,
    @ColumnInfo(name = "bytes")
    val bytes: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtMillis: Long,
)

/** An episode with what the user did with it, read in one query. */
data class PodcastEpisodeWithState(
    @Embedded
    val episode: PodcastEpisodeEntity,
    @Embedded(prefix = "progress_")
    val progress: PodcastProgressColumns?,
    @Embedded(prefix = "download_")
    val download: PodcastDownloadColumns?,
)

data class PodcastProgressColumns(
    @ColumnInfo(name = "position_ms")
    val positionMillis: Long,
    @ColumnInfo(name = "is_finished")
    val isFinished: Boolean,
)

data class PodcastDownloadColumns(
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "bytes")
    val bytes: Long,
)
