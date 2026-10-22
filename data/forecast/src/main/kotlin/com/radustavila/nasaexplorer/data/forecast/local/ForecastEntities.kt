package com.radustavila.nasaexplorer.data.forecast.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/** One cached place: when it was fetched and what MET Norway said about re-fetching it. */
@Entity(tableName = "forecast_place", primaryKeys = ["latitude_e2", "longitude_e2"])
data class ForecastPlaceEntity(
    @ColumnInfo(name = "latitude_e2") val latitudeE2: Int,
    @ColumnInfo(name = "longitude_e2") val longitudeE2: Int,
    /** Epoch millis of the last download or 304. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    /** Epoch millis before which MET Norway must not be asked again. */
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    /** The response's `Last-Modified`, verbatim, for `If-Modified-Since`. */
    @ColumnInfo(name = "last_modified") val lastModified: String?,
    /** Epoch millis of MET Norway's model run (`meta.updated_at`). */
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
)

/** One forecast step of a place; cover and humidity in percent. */
@Entity(tableName = "forecast_step", primaryKeys = ["latitude_e2", "longitude_e2", "time"])
data class ForecastStepEntity(
    @ColumnInfo(name = "latitude_e2") val latitudeE2: Int,
    @ColumnInfo(name = "longitude_e2") val longitudeE2: Int,
    /** Epoch millis. */
    val time: Long,
    @ColumnInfo(name = "cloud_cover") val cloudCover: Double,
    @ColumnInfo(name = "low_cloud_cover") val lowCloudCover: Double?,
    @ColumnInfo(name = "medium_cloud_cover") val mediumCloudCover: Double?,
    @ColumnInfo(name = "high_cloud_cover") val highCloudCover: Double?,
    @ColumnInfo(name = "fog_cover") val fogCover: Double?,
    @ColumnInfo(name = "relative_humidity") val relativeHumidity: Double?,
    @ColumnInfo(name = "wind_speed") val windSpeed: Double?,
    @ColumnInfo(name = "air_temperature") val airTemperature: Double?,
)
