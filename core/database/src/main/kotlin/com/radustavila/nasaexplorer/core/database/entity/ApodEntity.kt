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
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long = 0L,
)

/**
 * Partial-update projection used by [com.radustavila.nasaexplorer.core.database.dao.ApodDao].
 *
 * Remote payloads must never clobber `is_favorite`, which is local user state. Updating through a
 * projection that simply does not contain the column makes that impossible rather than merely
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
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long,
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
    cachedAtEpochMillis = cachedAtEpochMillis,
)
