package com.radustavila.nasaexplorer.data.climate.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/** One value of one indicator. Each refresh replaces all of that indicator's rows. */
@Entity(tableName = "climate_point", primaryKeys = ["indicator", "date"])
data class ClimatePointEntity(
    /** `ClimateIndicator.key`. */
    val indicator: String,
    /** ISO-8601 date: sorts chronologically as text. */
    val date: String,
    val value: Double,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
)
