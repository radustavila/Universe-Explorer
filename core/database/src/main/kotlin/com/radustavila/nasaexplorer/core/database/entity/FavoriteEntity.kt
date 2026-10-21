package com.radustavila.nasaexplorer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A favourite of any source, added in schema v4 (replacing the `is_favorite` flag on `apods`).
 *
 * Keyed by `(source, item_id)`: `source` is a persisted `FavoriteSource.key`, `item_id` the
 * source's own identifier (an ISO date for APOD). `title` and `image_url` are a snapshot taken when
 * the item was saved, so the Favourites tab renders without the source's cache - which eviction is
 * free to trim.
 *
 * `local_image_path` (schema v7) points at the offline copy of the snapshot image, relative to the
 * offline favourites directory, once it has been downloaded; `local_image_bytes` is its size. Both
 * are owned by the offline image download and cleared whenever the snapshot image changes.
 */
@Entity(
    tableName = "favorite",
    primaryKeys = ["source", "item_id"],
    indices = [Index(value = ["saved_at_epoch_millis"])],
)
data class FavoriteEntity(
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "item_id")
    val itemId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "image_url")
    val imageUrl: String?,
    @ColumnInfo(name = "saved_at_epoch_millis")
    val savedAtEpochMillis: Long,
    @ColumnInfo(name = "local_image_path")
    val localImagePath: String? = null,
    @ColumnInfo(name = "local_image_bytes", defaultValue = "0")
    val localImageBytes: Long = 0,
)

/** The offline copy of one favourite's snapshot image. */
data class FavoriteLocalImage(
    @ColumnInfo(name = "image_url")
    val imageUrl: String,
    @ColumnInfo(name = "local_image_path")
    val localImagePath: String,
)
