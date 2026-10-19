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
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "livestream_url") val livestreamUrl: String?,
    @ColumnInfo(name = "page_url") val pageUrl: String,
)

/**
 * Single-row refresh bookkeeping: the earliest time the next Launch Library 2 call may be made. Kept
 * in the same database as the cache so both survive (and are cleared) together.
 */
@Entity(tableName = "launch_refresh")
data class LaunchRefreshEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    @ColumnInfo(name = "next_allowed_at_epoch_millis") val nextAllowedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
