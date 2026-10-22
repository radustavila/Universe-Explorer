package com.radustavila.nasaexplorer.data.forecast.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * MET Norway Locationforecast 2.0 `complete` (GeoJSON). Only what the stargazing score reads is
 * declared; the rest (pressure, precipitation, symbols) is ignored.
 */
@Serializable
data class LocationForecastDto(
    val properties: ForecastPropertiesDto = ForecastPropertiesDto(),
)

@Serializable
data class ForecastPropertiesDto(
    val meta: ForecastMetaDto? = null,
    val timeseries: List<ForecastTimeStepDto> = emptyList(),
)

@Serializable
data class ForecastMetaDto(
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ForecastTimeStepDto(
    val time: String,
    val data: ForecastTimeStepDataDto = ForecastTimeStepDataDto(),
)

@Serializable
data class ForecastTimeStepDataDto(
    val instant: ForecastInstantDto = ForecastInstantDto(),
)

@Serializable
data class ForecastInstantDto(
    val details: ForecastDetailsDto = ForecastDetailsDto(),
)

/** Values at the step's instant; cover and humidity in percent. */
@Serializable
data class ForecastDetailsDto(
    @SerialName("cloud_area_fraction") val cloudAreaFraction: Double? = null,
    @SerialName("cloud_area_fraction_low") val cloudAreaFractionLow: Double? = null,
    @SerialName("cloud_area_fraction_medium") val cloudAreaFractionMedium: Double? = null,
    @SerialName("cloud_area_fraction_high") val cloudAreaFractionHigh: Double? = null,
    @SerialName("fog_area_fraction") val fogAreaFraction: Double? = null,
    @SerialName("relative_humidity") val relativeHumidity: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("air_temperature") val airTemperature: Double? = null,
)
