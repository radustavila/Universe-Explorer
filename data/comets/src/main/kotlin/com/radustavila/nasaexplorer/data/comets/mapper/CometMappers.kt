package com.radustavila.nasaexplorer.data.comets.mapper

import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometEphemerisPoint
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.data.comets.local.CometEntity
import com.radustavila.nasaexplorer.data.comets.local.CometEphemerisEntity
import com.radustavila.nasaexplorer.data.comets.local.CometWithEphemeris
import com.radustavila.nasaexplorer.data.comets.remote.SbdbCometQuery
import com.radustavila.nasaexplorer.data.comets.remote.dto.CometCandidateDto
import com.radustavila.nasaexplorer.data.comets.remote.dto.CometEphemerisRowDto
import java.time.Instant

internal fun CometCandidateDto.toEntity(fetchedAtMillis: Long) = CometEntity(
    designation = designation,
    name = fullName,
    absoluteMagnitude = absoluteMagnitude,
    slopeParameter = slopeParameter,
    perihelionDistanceAu = perihelionDistanceAu,
    eccentricity = eccentricity,
    perihelionTimeMillis = perihelionJulianDate?.let { SbdbCometQuery.julianDateToInstant(it).toEpochMilli() },
    fetchedAtMillis = fetchedAtMillis,
)

internal fun CometEphemerisRowDto.toEntity(designation: String, fetchedAtMillis: Long) = CometEphemerisEntity(
    designation = designation,
    timeMillis = epochMillis,
    rightAscensionDegrees = rightAscensionDegrees,
    declinationDegrees = declinationDegrees,
    magnitude = totalMagnitude,
    sunDistanceAu = sunDistanceAu,
    earthDistanceAu = earthDistanceAu,
    fetchedAtMillis = fetchedAtMillis,
)

internal fun CometWithEphemeris.toDomain() = Comet(
    designation = comet.designation,
    name = comet.name,
    absoluteMagnitude = comet.absoluteMagnitude,
    slopeParameter = comet.slopeParameter,
    perihelionDistanceAu = comet.perihelionDistanceAu,
    eccentricity = comet.eccentricity,
    perihelionTime = comet.perihelionTimeMillis?.let(Instant::ofEpochMilli),
    ephemeris = ephemeris.sortedBy { it.timeMillis }.map { it.toDomain() },
)

private fun CometEphemerisEntity.toDomain() = CometEphemerisPoint(
    time = Instant.ofEpochMilli(timeMillis),
    position = EquatorialCoordinate(rightAscensionDegrees, declinationDegrees),
    magnitude = magnitude,
    sunDistanceAu = sunDistanceAu,
    earthDistanceAu = earthDistanceAu,
)
