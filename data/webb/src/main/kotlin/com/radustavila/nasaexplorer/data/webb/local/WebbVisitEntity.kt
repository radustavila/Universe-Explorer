package com.radustavila.nasaexplorer.data.webb.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/** One prime visit of the stored schedule. Each refresh replaces the whole table. */
@Entity(tableName = "webb_visit", primaryKeys = ["visit_id", "start"])
data class WebbVisitEntity(
    @ColumnInfo(name = "visit_id") val visitId: String,
    @ColumnInfo(name = "start") val startMillis: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @ColumnInfo(name = "visit_type") val visitType: String,
    val instrument: String,
    @ColumnInfo(name = "target_name") val targetName: String?,
    val category: String?,
    val keywords: String?,
    /** The parallel instruments joined with [PARALLEL_SEPARATOR]; empty for none. */
    val parallels: String,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
) {
    companion object {
        /** Never part of an instrument's name in STScI's files. */
        const val PARALLEL_SEPARATOR = "|"
    }
}
