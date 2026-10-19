package com.radustavila.nasaexplorer.data.spaceweather.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One 3-hour Kp slot. Instants are epoch milliseconds. */
@Entity(tableName = "kp_readings")
data class KpReadingEntity(
    @PrimaryKey @ColumnInfo(name = "start") val start: Long,
    @ColumnInfo(name = "kp") val kp: Double,
    /** `KpKind` name. */
    @ColumnInfo(name = "kind") val kind: String,
)

/** The latest 1-minute Kp estimate; a single row. */
@Entity(tableName = "current_kp")
data class CurrentKpEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = SINGLE_ROW,
    @ColumnInfo(name = "time") val time: Long,
    @ColumnInfo(name = "kp") val kp: Double,
) {
    companion object {
        const val SINGLE_ROW = 0
    }
}

/**
 * The latest aurora grid; a single row. [grid] stores the 360 × 181 probabilities as one byte each
 * (65 KB) instead of 65,160 rows.
 */
@Entity(tableName = "aurora_forecast")
class AuroraForecastEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: Int = CurrentKpEntity.SINGLE_ROW,
    @ColumnInfo(name = "observed_at") val observedAt: Long,
    @ColumnInfo(name = "forecast_for") val forecastFor: Long,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "grid", typeAffinity = ColumnInfo.BLOB) val grid: ByteArray,
)

@Entity(tableName = "solar_flares")
data class SolarFlareEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "class_type") val classType: String,
    @ColumnInfo(name = "begin_time") val beginTime: Long,
    @ColumnInfo(name = "peak_time") val peakTime: Long?,
    @ColumnInfo(name = "end_time") val endTime: Long?,
    @ColumnInfo(name = "source_location") val sourceLocation: String?,
    @ColumnInfo(name = "active_region") val activeRegion: Int?,
    @ColumnInfo(name = "link") val link: String?,
)

@Entity(tableName = "coronal_mass_ejections")
data class CoronalMassEjectionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "speed_km_s") val speedKmPerSecond: Double?,
    @ColumnInfo(name = "half_angle") val halfAngleDegrees: Double?,
    @ColumnInfo(name = "source_location") val sourceLocation: String?,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "earth_arrival") val earthArrival: Long?,
    @ColumnInfo(name = "earth_glancing_blow") val isEarthGlancingBlow: Boolean,
    @ColumnInfo(name = "link") val link: String?,
)
