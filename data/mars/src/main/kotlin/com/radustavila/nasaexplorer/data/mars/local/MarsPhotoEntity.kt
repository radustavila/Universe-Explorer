package com.radustavila.nasaexplorer.data.mars.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A rover's raw image, as last downloaded. */
@Entity(
    tableName = "mars_photo",
    indices = [Index("rover", "sol")],
)
data class MarsPhotoEntity(
    @PrimaryKey val id: String,
    /** [com.radustavila.nasaexplorer.core.model.MarsRover.key]. */
    val rover: String,
    val sol: Int,
    @ColumnInfo(name = "camera_id") val cameraId: String,
    @ColumnInfo(name = "camera_name") val cameraName: String,
    /** Epoch millis. */
    @ColumnInfo(name = "taken_at") val takenAt: Long?,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    val description: String?,
    val credit: String?,
    /** Epoch millis of the last download that included this photo, for eviction. */
    @ColumnInfo(name = "cached_at") val cachedAt: Long,
)
