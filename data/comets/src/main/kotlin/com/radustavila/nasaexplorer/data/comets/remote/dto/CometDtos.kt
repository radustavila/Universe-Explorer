package com.radustavila.nasaexplorer.data.comets.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The SBDB query API's table: column names in [fields], one array of values per object in [data]
 * (numbers come as strings, missing ones as `null`). An empty answer has an empty [data].
 */
@Serializable
data class SbdbQueryDto(
    val fields: List<String> = emptyList(),
    val data: List<List<JsonElement>> = emptyList(),
) {
    /** The rows as name → value, the value null where SBDB sent `null` or nothing. */
    fun rows(): List<Map<String, String?>> = data.map { values ->
        fields.withIndex().associate { (index, name) ->
            val value = values.getOrNull(index)
            name to (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
        }
    }
}

/** A comet near perihelion as SBDB lists it, before Horizons is asked about it. */
data class CometCandidateDto(
    val designation: String,
    val fullName: String,
    val absoluteMagnitude: Double?,
    val slopeParameter: Double?,
    val perihelionDistanceAu: Double,
    val eccentricity: Double,
    /** Time of perihelion, Julian date (TDB). */
    val perihelionJulianDate: Double?,
)

/** The Horizons API's JSON envelope: the ephemeris is plain text inside [result]. */
@Serializable
data class HorizonsResponseDto(
    val result: String? = null,
    val error: String? = null,
)

/** One row of a Horizons observer table with quantities 1, 9, 19 and 20, angles in degrees. */
data class CometEphemerisRowDto(
    val epochMillis: Long,
    val rightAscensionDegrees: Double,
    val declinationDegrees: Double,
    /** T-mag; null where Horizons printed `n.a.`. */
    val totalMagnitude: Double?,
    val sunDistanceAu: Double,
    val earthDistanceAu: Double,
)
