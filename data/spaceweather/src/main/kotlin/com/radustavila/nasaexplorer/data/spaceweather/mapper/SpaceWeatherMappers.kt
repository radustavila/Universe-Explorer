package com.radustavila.nasaexplorer.data.spaceweather.mapper

import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpKind
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import com.radustavila.nasaexplorer.data.spaceweather.local.AuroraForecastEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.CoronalMassEjectionEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.CurrentKpEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.KpReadingEntity
import com.radustavila.nasaexplorer.data.spaceweather.local.SolarFlareEntity
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.CmeDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.FlareDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpForecastDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.KpOneMinuteDto
import com.radustavila.nasaexplorer.data.spaceweather.remote.dto.OvationDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * NOAA and DONKI both write UTC, in three spellings: "2026-10-12T00:00:00" (no zone),
 * "2026-10-19T14:08:00Z" and DONKI's minute precision "2026-09-19T17:57Z". Null when unreadable.
 */
internal fun parseUtc(text: String?): Instant? {
    val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        LocalDateTime.parse(value.removeSuffix("Z").replace(' ', 'T')).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
}

private val KP_RANGE = 0.0..9.0

/** Null for rows without a usable time or Kp. */
internal fun KpForecastDto.toEntityOrNull(): KpReadingEntity? {
    val start = parseUtc(timeTag) ?: return null
    val value = kp?.takeIf { it in KP_RANGE } ?: return null
    val kind = when (observed?.lowercase()) {
        "observed" -> KpKind.OBSERVED
        "estimated" -> KpKind.ESTIMATED
        "predicted" -> KpKind.PREDICTED
        else -> return null
    }
    return KpReadingEntity(start = start.toEpochMilli(), kp = value, kind = kind.name)
}

/** The newest minute with an estimate. */
internal fun List<KpOneMinuteDto>.latestToEntityOrNull(): CurrentKpEntity? =
    mapNotNull { dto ->
        val time = parseUtc(dto.timeTag) ?: return@mapNotNull null
        val kp = (dto.estimatedKp ?: dto.kpIndex?.toDouble())?.takeIf { it in KP_RANGE } ?: return@mapNotNull null
        CurrentKpEntity(time = time.toEpochMilli(), kp = kp)
    }.maxByOrNull { it.time }

/**
 * Packs the grid into one byte per point. The triple order is read from "Data Format", so a
 * reordering upstream does not silently transpose the map; points missing from the answer stay 0.
 * Null when the answer has no usable point.
 */
internal fun OvationDto.toEntityOrNull(fetchedAt: Instant): AuroraForecastEntity? {
    val observedAt = parseUtc(observationTime) ?: return null
    val forecastFor = parseUtc(forecastTime) ?: observedAt
    val fields = dataFormat?.trim('[', ']', ' ')?.split(',')?.map { it.trim().lowercase() }.orEmpty()
    val lonIndex = fields.indexOf("longitude").takeIf { it >= 0 } ?: 0
    val latIndex = fields.indexOf("latitude").takeIf { it >= 0 } ?: 1
    val valueIndex = fields.indexOf("aurora").takeIf { it >= 0 } ?: 2

    val grid = ByteArray(AuroraForecast.LONGITUDES * AuroraForecast.LATITUDES)
    var points = 0
    for (point in coordinates) {
        if (point.size <= maxOf(lonIndex, latIndex, valueIndex)) continue
        val latitude = point[latIndex].roundToInt()
        if (latitude !in -90..90) continue
        val longitude = Math.floorMod(point[lonIndex].roundToInt(), AuroraForecast.LONGITUDES)
        grid[AuroraForecast.index(longitude, latitude)] = point[valueIndex].roundToInt().coerceIn(0, 100).toByte()
        points++
    }
    if (points == 0) return null
    return AuroraForecastEntity(
        observedAt = observedAt.toEpochMilli(),
        forecastFor = forecastFor.toEpochMilli(),
        fetchedAt = fetchedAt.toEpochMilli(),
        grid = grid,
    )
}

internal fun FlareDto.toEntityOrNull(): SolarFlareEntity? {
    val flareId = id?.takeIf { it.isNotBlank() } ?: return null
    val begin = parseUtc(beginTime) ?: return null
    val flareClass = classType?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return SolarFlareEntity(
        id = flareId,
        classType = flareClass,
        beginTime = begin.toEpochMilli(),
        peakTime = parseUtc(peakTime)?.toEpochMilli(),
        endTime = parseUtc(endTime)?.toEpochMilli(),
        sourceLocation = sourceLocation?.takeIf { it.isNotBlank() },
        activeRegion = activeRegion,
        link = link,
    )
}

/**
 * Takes the analysis DONKI marks most accurate, and from its newest WSA-ENLIL run whether the CME
 * is expected at Earth.
 */
internal fun CmeDto.toEntityOrNull(): CoronalMassEjectionEntity? {
    val cmeId = id?.takeIf { it.isNotBlank() } ?: return null
    val start = parseUtc(startTime) ?: return null
    val analysis = cmeAnalyses.orEmpty().let { list -> list.firstOrNull { it.isMostAccurate } ?: list.lastOrNull() }
    val earthRun = analysis?.enlilList.orEmpty()
        .sortedBy { parseUtc(it.modelCompletionTime) }
        .lastOrNull { parseUtc(it.estimatedShockArrivalTime) != null }
    return CoronalMassEjectionEntity(
        id = cmeId,
        startTime = start.toEpochMilli(),
        speedKmPerSecond = analysis?.speed,
        halfAngleDegrees = analysis?.halfAngle,
        sourceLocation = sourceLocation?.takeIf { it.isNotBlank() },
        note = note?.takeIf { it.isNotBlank() },
        earthArrival = parseUtc(earthRun?.estimatedShockArrivalTime)?.toEpochMilli(),
        isEarthGlancingBlow = earthRun?.isEarthGB == true,
        link = link,
    )
}

internal fun KpReadingEntity.toDomain() = KpReading(
    start = Instant.ofEpochMilli(start),
    kp = kp,
    kind = runCatching { KpKind.valueOf(kind) }.getOrDefault(KpKind.PREDICTED),
)

internal fun CurrentKpEntity.toDomain() = CurrentKp(time = Instant.ofEpochMilli(time), kp = kp)

internal fun AuroraForecastEntity.toDomain(): AuroraForecast? =
    runCatching {
        AuroraForecast(
            observedAt = Instant.ofEpochMilli(observedAt),
            forecastFor = Instant.ofEpochMilli(forecastFor),
            probabilities = grid,
        )
    }.getOrNull()

internal fun SolarFlareEntity.toDomain() = SolarFlare(
    id = id,
    classType = classType,
    beginTime = Instant.ofEpochMilli(beginTime),
    peakTime = peakTime?.let(Instant::ofEpochMilli),
    endTime = endTime?.let(Instant::ofEpochMilli),
    sourceLocation = sourceLocation,
    activeRegion = activeRegion,
    link = link,
)

internal fun CoronalMassEjectionEntity.toDomain() = CoronalMassEjection(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    speedKmPerSecond = speedKmPerSecond,
    halfAngleDegrees = halfAngleDegrees,
    sourceLocation = sourceLocation,
    note = note,
    earthArrival = earthArrival?.let(Instant::ofEpochMilli),
    isEarthGlancingBlow = isEarthGlancingBlow,
    link = link,
)
