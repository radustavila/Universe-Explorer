package com.radustavila.nasaexplorer.data.spaceweather.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One row of SWPC's `noaa-planetary-k-index-forecast.json`: a 3-hour slot, [observed] being
 * "observed", "estimated" or "predicted". Times are UTC without a zone suffix.
 */
@Serializable
data class KpForecastDto(
    @SerialName("time_tag") val timeTag: String? = null,
    val kp: Double? = null,
    val observed: String? = null,
    @SerialName("noaa_scale") val noaaScale: String? = null,
)

/** One minute of SWPC's `planetary_k_index_1m.json`. */
@Serializable
data class KpOneMinuteDto(
    @SerialName("time_tag") val timeTag: String? = null,
    @SerialName("estimated_kp") val estimatedKp: Double? = null,
    @SerialName("kp_index") val kpIndex: Int? = null,
)

/**
 * SWPC's OVATION aurora nowcast. [coordinates] holds one triple per grid point, in the order
 * [dataFormat] names ("[Longitude, Latitude, Aurora]").
 */
@Serializable
data class OvationDto(
    @SerialName("Observation Time") val observationTime: String? = null,
    @SerialName("Forecast Time") val forecastTime: String? = null,
    @SerialName("Data Format") val dataFormat: String? = null,
    val coordinates: List<List<Double>> = emptyList(),
)

/** A DONKI solar flare (`get/FLR`). Times look like "2026-09-19T17:57Z". */
@Serializable
data class FlareDto(
    @SerialName("flrID") val id: String? = null,
    val classType: String? = null,
    val beginTime: String? = null,
    val peakTime: String? = null,
    val endTime: String? = null,
    val sourceLocation: String? = null,
    @SerialName("activeRegionNum") val activeRegion: Int? = null,
    val link: String? = null,
)

/** A DONKI coronal mass ejection (`get/CME`). */
@Serializable
data class CmeDto(
    @SerialName("activityID") val id: String? = null,
    val startTime: String? = null,
    val sourceLocation: String? = null,
    val note: String? = null,
    val link: String? = null,
    val cmeAnalyses: List<CmeAnalysisDto>? = null,
)

@Serializable
data class CmeAnalysisDto(
    val isMostAccurate: Boolean = false,
    val speed: Double? = null,
    val halfAngle: Double? = null,
    val enlilList: List<EnlilRunDto>? = null,
)

/** One WSA-ENLIL model run for a CME; a shock arrival time means the model expects it at Earth. */
@Serializable
data class EnlilRunDto(
    val modelCompletionTime: String? = null,
    val estimatedShockArrivalTime: String? = null,
    val isEarthGB: Boolean? = null,
)
