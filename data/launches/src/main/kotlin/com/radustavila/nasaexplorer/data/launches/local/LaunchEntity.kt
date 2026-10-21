package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached upcoming launch. The table is replaced wholesale on every refresh: it mirrors "the next
 * few launches" and has no local state worth preserving.
 */
@Entity(tableName = "launches")
data class LaunchEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "net_epoch_millis") val netEpochMillis: Long,
    /** Launch Library 2 status id; see `LaunchStatus`. */
    @ColumnInfo(name = "status_id") val statusId: Int,
    val rocket: String,
    val pad: String,
    /** Launch service provider's name; null when the API omits it. */
    val provider: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "livestream_url") val livestreamUrl: String?,
    @ColumnInfo(name = "page_url") val pageUrl: String,
)

