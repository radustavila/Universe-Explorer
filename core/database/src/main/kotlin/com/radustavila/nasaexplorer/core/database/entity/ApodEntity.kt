package com.radustavila.nasaexplorer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached APOD row.
 *
 * `date` (ISO-8601, e.g. `2026-09-10`) is the primary key: NASA publishes one entry per day and
 * offers no other stable identifier, which makes duplicate rows structurally impossible.
 *
 * Dates and timestamps are stored as primitives (TEXT/INTEGER) rather than through type converters:
 * ISO-8601 text sorts chronologically in SQL, which is exactly what the feed and the date-window
 * queries need.
 */
@Entity(
    tableName = "apods",
    indices = [
        Index(value = ["is_favorite"]),
        Index(value = ["is_in_feed"]),
        Index(value = ["media_type"]),
    ],
)
data class ApodEntity(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "explanation")
    val explanation: String,
    @ColumnInfo(name = "media_type")
    val mediaType: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "hd_url")
    val hdUrl: String?,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,
    @ColumnInfo(name = "copyright")
    val copyright: String?,
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
    /** When the row first entered the cache; never overwritten by a later sync. */
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long = 0L,
    /** When the row was last confirmed against the remote API. Added in schema v2. */
    @ColumnInfo(name = "last_synced_at_epoch_millis", defaultValue = "0")
    val lastSyncedAtEpochMillis: Long = 0L,
    /**
     * Whether the row belongs to the contiguous date window the feed pages through. Added in
     * schema v3.
     *
     * A day opened straight from the calendar is cached as an isolated row far outside that window;
     * showing it in the feed would put a lone 2001 entry directly under this week's. It is still a
     * first-class cache entry - readable offline, favouritable - just not part of the feed until
     * paging actually reaches it.
     */
    @ColumnInfo(name = "is_in_feed", defaultValue = "1")
    val isInFeed: Boolean = true,
)

/**
 * Partial-update projection used by [com.radustavila.nasaexplorer.core.database.dao.ApodDao].
 *
 * Remote payloads must never clobber `is_favorite`, which is local user state, nor
 * `cached_at_epoch_millis`, which records when the entry first entered the cache. Updating through
 * a projection that simply does not contain those columns makes that impossible rather than merely
 * unlikely.
 */
data class ApodRemoteContent(
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "explanation")
    val explanation: String,
    @ColumnInfo(name = "media_type")
    val mediaType: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "hd_url")
    val hdUrl: String?,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,
    @ColumnInfo(name = "copyright")
    val copyright: String?,
    @ColumnInfo(name = "last_synced_at_epoch_millis")
    val lastSyncedAtEpochMillis: Long,
)

fun ApodEntity.toRemoteContent(): ApodRemoteContent = ApodRemoteContent(
    date = date,
    title = title,
    explanation = explanation,
    mediaType = mediaType,
    url = url,
    hdUrl = hdUrl,
    thumbnailUrl = thumbnailUrl,
    copyright = copyright,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)
