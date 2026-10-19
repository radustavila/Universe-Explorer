package com.radustavila.nasaexplorer.data.nasaimages.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A library item kept locally: either on a rail, or saved by the user. Search results are not
 * stored (search is online-first).
 */
@Entity(tableName = "nasa_media")
data class NasaMediaEntity(
    @PrimaryKey @ColumnInfo(name = "nasa_id") val nasaId: String,
    val title: String,
    val description: String,
    /** [com.radustavila.nasaexplorer.core.model.NasaMediaType.apiValue]. */
    @ColumnInfo(name = "media_type") val mediaType: String,
    /** ISO-8601 date. */
    @ColumnInfo(name = "date_created") val dateCreated: String?,
    val center: String?,
    val creator: String?,
    /** One keyword per line. */
    val keywords: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "full_image_url") val fullImageUrl: String?,
    @ColumnInfo(name = "original_url") val originalUrl: String?,
    @ColumnInfo(name = "alt_text") val altText: String?,
    /** Epoch millis of the last write, for eviction. */
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)

/** Membership of an item in a rail, at a position; a rail refresh replaces all of its rows. */
@Entity(
    tableName = "rail_item",
    primaryKeys = ["rail", "nasa_id"],
    indices = [Index("nasa_id")],
)
data class RailItemEntity(
    /** [com.radustavila.nasaexplorer.core.model.GalleryRail.key]. */
    val rail: String,
    @ColumnInfo(name = "nasa_id") val nasaId: String,
    val position: Int,
)

/** A rail row joined with its item. */
data class RailMediaRow(
    val rail: String,
    @Embedded val media: NasaMediaEntity,
)
