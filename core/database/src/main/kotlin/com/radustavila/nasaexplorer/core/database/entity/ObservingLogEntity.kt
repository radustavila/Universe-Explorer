package com.radustavila.nasaexplorer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One "seen" mark of the observing log (F23), added in schema v8. It lives in this database because
 * backups keep it, and no cache evictor touches it.
 *
 * Keyed by `object_id`, OpenNGC's name of the deep-sky object ("NGC0224"); `observed_on` is an ISO
 * date, `conditions` a persisted `ObservingConditions` name.
 */
@Entity(tableName = "observing_log")
data class ObservingLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "object_id")
    val objectId: String,
    @ColumnInfo(name = "observed_on")
    val observedOn: String,
    @ColumnInfo(name = "conditions")
    val conditions: String?,
    @ColumnInfo(name = "note")
    val note: String?,
    @ColumnInfo(name = "logged_at_epoch_millis")
    val loggedAtEpochMillis: Long,
)
