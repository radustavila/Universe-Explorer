package com.radustavila.nasaexplorer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Cached EPIC photo metadata (the pixels live in Coil's disk cache). Added in schema v4.
 *
 * Keyed by (`collection`, `identifier`): EPIC's identifier is the capture timestamp, which the
 * natural and enhanced sets share. `date` is the UTC capture day in ISO-8601, denormalised from the
 * timestamp because every query selects or trims by day.
 *
 * Nothing in this table is user state, so a sync may replace a day's rows wholesale.
 */
@Entity(
    tableName = "epic_images",
    primaryKeys = ["collection", "identifier"],
    indices = [Index(value = ["collection", "date"])],
)
data class EpicImageEntity(
    @ColumnInfo(name = "collection")
    val collection: String,
    @ColumnInfo(name = "identifier")
    val identifier: String,
    @ColumnInfo(name = "date")
    val date: String,
    @ColumnInfo(name = "captured_at_epoch_millis")
    val capturedAtEpochMillis: Long,
    @ColumnInfo(name = "caption")
    val caption: String,
    @ColumnInfo(name = "centroid_latitude")
    val centroidLatitude: Double,
    @ColumnInfo(name = "centroid_longitude")
    val centroidLongitude: Double,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "cached_at_epoch_millis")
    val cachedAtEpochMillis: Long,
)

/**
 * A day EPIC has published photos for, from its `/all` listing. Added in schema v4.
 *
 * Kept separate from [EpicImageEntity] because the listing covers the whole archive (thousands of
 * days) while photo metadata is only cached for the last week.
 */
@Entity(
    tableName = "epic_available_dates",
    primaryKeys = ["collection", "date"],
)
data class EpicAvailableDateEntity(
    @ColumnInfo(name = "collection")
    val collection: String,
    @ColumnInfo(name = "date")
    val date: String,
)
