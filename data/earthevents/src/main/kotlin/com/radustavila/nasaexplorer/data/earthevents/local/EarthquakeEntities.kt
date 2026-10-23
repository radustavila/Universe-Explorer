package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One quake as one feed listed it. Keyed by feed and id, so each feed is replaced on its own (the
 * other may have answered 304); a quake in both feeds is stored twice and read once.
 */
@Entity(
    tableName = "earthquake",
    primaryKeys = ["feed", "id"],
    indices = [Index("time")],
)
data class EarthquakeEntity(
    /** `EarthquakeFeed.key`. */
    @ColumnInfo(name = "feed")
    val feed: String,
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "magnitude")
    val magnitude: Double,
    @ColumnInfo(name = "place")
    val place: String?,
    /** Origin time, epoch millis. */
    @ColumnInfo(name = "time")
    val timeMillis: Long,
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    @ColumnInfo(name = "depth_km")
    val depthKm: Double,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "felt")
    val felt: Int?,
    /** `EarthquakeAlertLevel.key`. */
    @ColumnInfo(name = "alert")
    val alert: String?,
    @ColumnInfo(name = "tsunami")
    val tsunami: Boolean,
    @ColumnInfo(name = "significance")
    val significance: Int,
)

/** When a feed was last downloaded, and its `Last-Modified` for the next `If-Modified-Since`. */
@Entity(tableName = "earthquake_feed")
data class EarthquakeFeedEntity(
    @PrimaryKey
    @ColumnInfo(name = "feed")
    val feed: String,
    @ColumnInfo(name = "last_modified")
    val lastModified: String?,
    @ColumnInfo(name = "fetched_at")
    val fetchedAtMillis: Long,
)
