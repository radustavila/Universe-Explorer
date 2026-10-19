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
)
