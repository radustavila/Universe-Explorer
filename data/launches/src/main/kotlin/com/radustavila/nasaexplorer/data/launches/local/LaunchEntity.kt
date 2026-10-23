package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached launch, upcoming or past ([timeline]). Each list is replaced wholesale on its refresh:
 * it mirrors Launch Library 2 and has no local state worth preserving (reminders live in their own
 * DataStore).
 *
 * Boosters and webcasts are short lists only ever read with their launch, so they are stored as
 * JSON text (see `LaunchMappers`) rather than in tables of their own.
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
    /** `LaunchTimeline` name: which of Launch Library 2's lists the row came from. */
    @ColumnInfo(defaultValue = TIMELINE_UPCOMING) val timeline: String = TIMELINE_UPCOMING,
    /** Launch Library 2 NET precision id; see `NetPrecision`. */
    @ColumnInfo(name = "net_precision") val netPrecision: Int? = null,
    @ColumnInfo(name = "window_start_millis") val windowStartMillis: Long? = null,
    @ColumnInfo(name = "window_end_millis") val windowEndMillis: Long? = null,
    val probability: Int? = null,
    @ColumnInfo(name = "fail_reason") val failReason: String? = null,
    @ColumnInfo(name = "mission_name") val missionName: String? = null,
    @ColumnInfo(name = "mission_description") val missionDescription: String? = null,
    @ColumnInfo(name = "mission_type") val missionType: String? = null,
    @ColumnInfo(name = "orbit_name") val orbitName: String? = null,
    @ColumnInfo(name = "orbit_abbrev") val orbitAbbrev: String? = null,
    @ColumnInfo(name = "pad_latitude") val padLatitude: Double? = null,
    @ColumnInfo(name = "pad_longitude") val padLongitude: Double? = null,
    @ColumnInfo(name = "rocket_name") val rocketName: String? = null,
    @ColumnInfo(name = "rocket_variant") val rocketVariant: String? = null,
    /** JSON array of `StoredBooster`. */
    @ColumnInfo(defaultValue = EMPTY_JSON_ARRAY) val boosters: String = EMPTY_JSON_ARRAY,
    /** JSON array of `StoredWebcast`. */
    @ColumnInfo(defaultValue = EMPTY_JSON_ARRAY) val webcasts: String = EMPTY_JSON_ARRAY,
    @ColumnInfo(name = "crew_count", defaultValue = "0") val crewCount: Int = 0,
) {
    companion object {
        const val TIMELINE_UPCOMING = "UPCOMING"
        const val TIMELINE_PAST = "PAST"
        const val EMPTY_JSON_ARRAY = "[]"
    }
}
