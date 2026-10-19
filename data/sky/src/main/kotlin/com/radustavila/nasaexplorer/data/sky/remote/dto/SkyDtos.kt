package com.radustavila.nasaexplorer.data.sky.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One CelesTrak GP record (`gp.php?CATNR=25544&FORMAT=json`), the JSON form of the CCSDS Orbit Mean
 * Elements Message. Only the fields SGP4 needs; all optional so a malformed record is dropped.
 */
@Serializable
data class GpElementsDto(
    @SerialName("OBJECT_NAME") val objectName: String? = null,
    @SerialName("NORAD_CAT_ID") val noradCatId: Int? = null,
    /** UTC without a zone, e.g. `2026-10-19T04:14:45.011040`. */
    @SerialName("EPOCH") val epoch: String? = null,
    @SerialName("MEAN_MOTION") val meanMotion: Double? = null,
    @SerialName("ECCENTRICITY") val eccentricity: Double? = null,
    @SerialName("INCLINATION") val inclination: Double? = null,
    @SerialName("RA_OF_ASC_NODE") val raOfAscNode: Double? = null,
    @SerialName("ARG_OF_PERICENTER") val argOfPericenter: Double? = null,
    @SerialName("MEAN_ANOMALY") val meanAnomaly: Double? = null,
    @SerialName("BSTAR") val bstar: Double? = null,
    /** 0 for SGP4 elements, the only kind the propagator accepts. */
    @SerialName("EPHEMERIS_TYPE") val ephemerisType: Int? = null,
)

/** wheretheiss.at `v1/satellites/25544`. */
@Serializable
data class SatellitePositionDto(
    val id: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Kilometres (the default `units`). */
    val altitude: Double? = null,
    /** Kilometres per hour. */
    val velocity: Double? = null,
    /** "daylight" or "eclipsed". */
    val visibility: String? = null,
    /** Unix seconds. */
    val timestamp: Long? = null,
)
