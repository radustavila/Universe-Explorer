package com.radustavila.nasaexplorer.data.smallbodies.mapper

import com.radustavila.nasaexplorer.core.model.CloseApproach
import com.radustavila.nasaexplorer.core.model.Fireball
import com.radustavila.nasaexplorer.core.model.SentryRisk
import com.radustavila.nasaexplorer.core.model.SmallBodyDetails
import com.radustavila.nasaexplorer.data.smallbodies.local.CloseApproachEntity
import com.radustavila.nasaexplorer.data.smallbodies.local.CloseApproachWithSentry
import com.radustavila.nasaexplorer.data.smallbodies.local.FireballEntity
import com.radustavila.nasaexplorer.data.smallbodies.local.SentryObjectEntity
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodyDetailsEntity
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.JplRow
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.JplTableDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SbdbDto
import com.radustavila.nasaexplorer.data.smallbodies.remote.dto.SentryObjectDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** CAD's "2026-Oct-19 04:38" (TDB, which runs about a minute off UTC: close enough for a list). */
private val CAD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MMM-dd HH:mm", Locale.US)

/** The fireball API's "2026-10-04 03:14:45" (UTC). */
private val FIREBALL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.US)

private fun String.parseUtc(formatter: DateTimeFormatter): Instant? = try {
    LocalDateTime.parse(this, formatter).toInstant(ZoneOffset.UTC)
} catch (_: DateTimeParseException) {
    null
}

/** One fireball's radiated energy is given in units of 10^10 joules. */
private const val FIREBALL_ENERGY_UNIT_JOULES = 1e10

/**
 * Maps CAD's array-of-arrays by column name (see [JplTableDto]). Rows without a designation, a
 * readable date, a distance or a speed are dropped.
 */
internal fun JplTableDto.toCloseApproachEntities(): List<CloseApproachEntity> = rows().mapNotNull { row ->
    val designation = row["des"] ?: return@mapNotNull null
    CloseApproachEntity(
        designation = designation,
        name = row["fullname"].toDisplayName() ?: designation,
        time = row["cd"]?.parseUtc(CAD_DATE)?.toEpochMilli() ?: return@mapNotNull null,
        distanceAu = row.double("dist") ?: return@mapNotNull null,
        relativeVelocityKmPerSecond = row.double("v_rel") ?: return@mapNotNull null,
        absoluteMagnitude = row.double("h"),
        diameterKm = row.double("diameter"),
    )
}.distinctBy { it.designation to it.time }

/** JPL pads names and wraps provisional ones in parentheses: "       (2026 UK)" → "2026 UK". */
private fun String?.toDisplayName(): String? {
    val trimmed = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (trimmed.startsWith('(') && trimmed.endsWith(')')) trimmed.substring(1, trimmed.length - 1) else trimmed
}

internal fun JplTableDto.toFireballEntities(): List<FireballEntity> = rows().mapNotNull { row ->
    FireballEntity(
        time = row["date"]?.parseUtc(FIREBALL_DATE)?.toEpochMilli() ?: return@mapNotNull null,
        radiatedEnergyJoules = row.double("energy")?.times(FIREBALL_ENERGY_UNIT_JOULES) ?: return@mapNotNull null,
        impactEnergyKilotons = row.double("impact-e") ?: return@mapNotNull null,
        latitude = row.signed("lat", negative = "S"),
        longitude = row.signed("lon", negative = "W"),
        altitudeKm = row.double("alt"),
        velocityKmPerSecond = row.double("vel"),
    )
}.distinctBy { it.time }

/** Latitude and longitude come unsigned, with their hemisphere in a separate `-dir` column. */
private fun JplRow.signed(field: String, negative: String): Double? {
    val value = double(field) ?: return null
    return if (get("$field-dir").equals(negative, ignoreCase = true)) -value else value
}

internal fun List<SentryObjectDto>.toSentryEntities(): List<SentryObjectEntity> = mapNotNull { dto ->
    SentryObjectEntity(
        designation = dto.des?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
        impactProbability = dto.ip?.toDoubleOrNull() ?: return@mapNotNull null,
        palermoMax = dto.palermoMax?.toDoubleOrNull(),
        torinoMax = dto.torinoMax?.toIntOrNull(),
        potentialImpacts = dto.potentialImpacts,
        yearRange = dto.range?.takeIf { it.isNotBlank() },
    )
}.distinctBy { it.designation }

/** Null when the database does not know the object (it then answers only a message). */
internal fun SbdbDto.toEntityOrNull(fetchedAt: Instant): SmallBodyDetailsEntity? {
    val body = body ?: return null
    val designation = body.des?.takeIf { it.isNotBlank() } ?: return null
    val elements = orbit?.elements.orEmpty().associate { it.name to it.value?.toDoubleOrNull() }
    val physical = physicalParameters.associate { it.name to it }
    return SmallBodyDetailsEntity(
        designation = designation,
        name = body.fullname.toDisplayName() ?: designation,
        orbitClass = body.orbitClass?.name,
        isNearEarthObject = body.neo,
        isPotentiallyHazardous = body.pha,
        earthMoidAu = orbit?.moid?.toDoubleOrNull(),
        semiMajorAxisAu = elements["a"],
        eccentricity = elements["e"],
        inclinationDegrees = elements["i"],
        perihelionAu = elements["q"],
        aphelionAu = elements["ad"],
        orbitalPeriodDays = elements["per"],
        absoluteMagnitude = physical["H"]?.value?.toDoubleOrNull(),
        diameterKm = physical["diameter"]?.takeIf { it.units == null || it.units == "km" }?.value?.toDoubleOrNull(),
        firstObserved = orbit?.firstObserved,
        lastObserved = orbit?.lastObserved,
        observationsUsed = orbit?.observationsUsed,
        fetchedAt = fetchedAt.toEpochMilli(),
    )
}

internal fun CloseApproachWithSentry.toDomain() = CloseApproach(
    designation = approach.designation,
    name = approach.name,
    time = Instant.ofEpochMilli(approach.time),
    distanceAu = approach.distanceAu,
    relativeVelocityKmPerSecond = approach.relativeVelocityKmPerSecond,
    absoluteMagnitude = approach.absoluteMagnitude,
    diameterKm = approach.diameterKm,
    sentry = sentry?.toDomain(),
)

internal fun SentryObjectEntity.toDomain() = SentryRisk(
    designation = designation,
    impactProbability = impactProbability,
    palermoMax = palermoMax,
    torinoMax = torinoMax,
    potentialImpacts = potentialImpacts,
    yearRange = yearRange,
)

internal fun FireballEntity.toDomain() = Fireball(
    time = Instant.ofEpochMilli(time),
    radiatedEnergyJoules = radiatedEnergyJoules,
    impactEnergyKilotons = impactEnergyKilotons,
    latitude = latitude,
    longitude = longitude,
    altitudeKm = altitudeKm,
    velocityKmPerSecond = velocityKmPerSecond,
)

internal fun SmallBodyDetailsEntity.toDomain() = SmallBodyDetails(
    designation = designation,
    name = name,
    orbitClass = orbitClass,
    isNearEarthObject = isNearEarthObject,
    isPotentiallyHazardous = isPotentiallyHazardous,
    earthMoidAu = earthMoidAu,
    semiMajorAxisAu = semiMajorAxisAu,
    eccentricity = eccentricity,
    inclinationDegrees = inclinationDegrees,
    perihelionAu = perihelionAu,
    aphelionAu = aphelionAu,
    orbitalPeriodDays = orbitalPeriodDays,
    absoluteMagnitude = absoluteMagnitude,
    diameterKm = diameterKm,
    firstObserved = firstObserved,
    lastObserved = lastObserved,
    observationsUsed = observationsUsed,
    fetchedAt = Instant.ofEpochMilli(fetchedAt),
)
