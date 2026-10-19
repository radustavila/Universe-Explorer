package com.radustavila.nasaexplorer.data.sky.mapper

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsEntity
import com.radustavila.nasaexplorer.data.sky.remote.dto.GpElementsDto
import com.radustavila.nasaexplorer.data.sky.remote.dto.SatellitePositionDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Null for a record SGP4 cannot use: a missing element, a non-SGP4 ephemeris type, or values
 * outside their physical range.
 */
fun GpElementsDto.toEntityOrNull(fetchedAt: Instant): OrbitalElementsEntity? {
    val noradId = noradCatId ?: return null
    val epoch = epoch?.toUtcInstantOrNull() ?: return null
    val meanMotion = meanMotion?.takeIf { it > 0.0 } ?: return null
    val eccentricity = eccentricity?.takeIf { it >= 0.0 && it < 1.0 } ?: return null
    if (ephemerisType != null && ephemerisType != SGP4_EPHEMERIS_TYPE) return null

    return OrbitalElementsEntity(
        noradId = noradId,
        name = objectName?.trim()?.takeIf(String::isNotEmpty) ?: noradId.toString(),
        epochMillis = epoch.toEpochMilli(),
        meanMotion = meanMotion,
        eccentricity = eccentricity,
        inclinationDegrees = inclination ?: return null,
        raanDegrees = raOfAscNode ?: return null,
        argOfPericenterDegrees = argOfPericenter ?: return null,
        meanAnomalyDegrees = meanAnomaly ?: return null,
        bstar = bstar ?: 0.0,
        fetchedAtMillis = fetchedAt.toEpochMilli(),
    )
}

fun OrbitalElementsEntity.toDomain(): OrbitalElements = OrbitalElements(
    noradId = noradId,
    name = name,
    epoch = Instant.ofEpochMilli(epochMillis),
    meanMotion = meanMotion,
    eccentricity = eccentricity,
    inclinationDegrees = inclinationDegrees,
    raanDegrees = raanDegrees,
    argOfPericenterDegrees = argOfPericenterDegrees,
    meanAnomalyDegrees = meanAnomalyDegrees,
    bstar = bstar,
)

/** Null when the tracker answered without a usable position. */
fun SatellitePositionDto.toDomainOrNull(): SatellitePosition? {
    val latitude = latitude?.takeIf { it in -90.0..90.0 } ?: return null
    val longitude = longitude?.let(::normalizeLongitude) ?: return null
    return SatellitePosition(
        coordinate = GeoCoordinate(latitude, longitude),
        altitudeKm = altitude ?: return null,
        speedKmPerHour = velocity ?: return null,
        isSunlit = when (visibility) {
            "daylight" -> true
            "eclipsed" -> false
            else -> null
        },
        at = timestamp?.let(Instant::ofEpochSecond) ?: return null,
        isLive = true,
    )
}

private fun normalizeLongitude(longitude: Double): Double? {
    if (!longitude.isFinite()) return null
    var normalized = longitude.rem(FULL_TURN)
    if (normalized > HALF_TURN) normalized -= FULL_TURN
    if (normalized < -HALF_TURN) normalized += FULL_TURN
    return normalized
}

/** OMM epochs are UTC but written without a zone; older records may carry a `Z`. */
private fun String.toUtcInstantOrNull(): Instant? = try {
    if (endsWith("Z")) Instant.parse(this) else LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
} catch (_: DateTimeParseException) {
    null
}

private const val SGP4_EPHEMERIS_TYPE = 0
private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0
