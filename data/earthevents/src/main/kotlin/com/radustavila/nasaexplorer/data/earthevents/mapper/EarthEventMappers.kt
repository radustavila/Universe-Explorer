package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EarthEventGeometry
import com.radustavila.nasaexplorer.core.model.EarthEventShape
import com.radustavila.nasaexplorer.core.model.EarthEventSource
import com.radustavila.nasaexplorer.core.model.EventMagnitude
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventEntity
import com.radustavila.nasaexplorer.data.earthevents.local.GeometryRecord
import com.radustavila.nasaexplorer.data.earthevents.local.SourceRecord
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.EventDto
import com.radustavila.nasaexplorer.data.earthevents.remote.dto.GeometryDto
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The domain event for this DTO, or `null` when it is unusable: no id or title, a category the app
 * does not know, or no report with a valid date and position. Reports that fail on their own are
 * dropped; the rest are sorted oldest first, which is the order a storm track is drawn in.
 */
internal fun EventDto.toDomainOrNull(): EarthEvent? {
    val id = id?.takeIf(String::isNotBlank) ?: return null
    val title = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val category = categories.firstNotNullOfOrNull { dto -> dto.id?.let(EarthEventCategory::fromKey) }
        ?: return null
    val geometry = geometry.mapNotNull { it.toDomainOrNull() }.sortedBy(EarthEventGeometry::date)
    if (geometry.isEmpty()) return null

    return EarthEvent(
        id = id,
        title = title,
        category = category,
        sources = sources.mapNotNull { source ->
            val url = source.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (url == null) null else EarthEventSource(id = source.id.orEmpty(), url = url)
        },
        geometry = geometry,
        closedAt = closed?.toInstantOrNull(),
    )
}

private fun GeometryDto.toDomainOrNull(): EarthEventGeometry? {
    val date = date?.toInstantOrNull() ?: return null
    val shape = when (type) {
        "Point" -> coordinates?.toCoordinateOrNull()?.let(EarthEventShape::Point)
        "Polygon" -> coordinates?.toRingsOrNull()?.let(EarthEventShape::Polygon)
        else -> null
    } ?: return null
    val magnitude = magnitudeValue?.let { value ->
        magnitudeUnit?.trim()?.takeIf(String::isNotEmpty)?.let { unit -> EventMagnitude(value, unit) }
    }
    return EarthEventGeometry(date = date, shape = shape, magnitude = magnitude)
}

/** GeoJSON `[lon, lat]`, rejected when out of range (the map refuses such points). */
private fun JsonElement.toCoordinateOrNull(): GeoCoordinate? {
    val pair = this as? JsonArray ?: return null
    if (pair.size < 2) return null
    val longitude = pair[0].asDoubleOrNull() ?: return null
    val latitude = pair[1].asDoubleOrNull() ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return GeoCoordinate(latitude = latitude, longitude = longitude)
}

/** GeoJSON polygon rings; a ring with an invalid point, or fewer than three, is dropped. */
private fun JsonElement.toRingsOrNull(): List<List<GeoCoordinate>>? {
    val rings = (this as? JsonArray)?.mapNotNull { ring ->
        val points = (ring as? JsonArray)?.map { it.toCoordinateOrNull() ?: return@mapNotNull null }
        points?.takeIf { it.size >= MIN_RING_POINTS }
    }
    return rings?.takeIf(List<*>::isNotEmpty)
}

private fun JsonElement.asDoubleOrNull(): Double? = runCatching { jsonPrimitive.doubleOrNull }.getOrNull()

private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    null
}

internal fun EarthEvent.toEntity(json: Json, cachedAt: Instant): EarthEventEntity = EarthEventEntity(
    id = id,
    title = title,
    category = category.key,
    closedAtMillis = closedAt?.toEpochMilli(),
    lastUpdateMillis = lastUpdate.toEpochMilli(),
    sourcesJson = json.encodeToString(sources.map { SourceRecord(it.id, it.url) }),
    geometryJson = json.encodeToString(geometry.map { it.toRecord() }),
    cachedAtMillis = cachedAt.toEpochMilli(),
)

private fun EarthEventGeometry.toRecord(): GeometryRecord = when (val shape = shape) {
    is EarthEventShape.Point -> GeometryRecord(
        date = date.toEpochMilli(),
        point = shape.position.toPair(),
        magnitude = magnitude?.value,
        unit = magnitude?.unit,
    )

    is EarthEventShape.Polygon -> GeometryRecord(
        date = date.toEpochMilli(),
        rings = shape.rings.map { ring -> ring.map { it.toPair() } },
        magnitude = magnitude?.value,
        unit = magnitude?.unit,
    )
}

private fun GeoCoordinate.toPair(): List<Double> = listOf(latitude, longitude)

/** The cached event, or `null` if the row no longer decodes (e.g. after a schema change). */
internal fun EarthEventEntity.toDomainOrNull(json: Json): EarthEvent? = runCatching {
    val geometry = json.decodeFromString<List<GeometryRecord>>(geometryJson).mapNotNull { it.toDomainOrNull() }
    EarthEvent(
        id = id,
        title = title,
        category = EarthEventCategory.fromKey(category) ?: return null,
        sources = json.decodeFromString<List<SourceRecord>>(sourcesJson).map { EarthEventSource(it.id, it.url) },
        geometry = geometry.ifEmpty { return null },
        closedAt = closedAtMillis?.let(Instant::ofEpochMilli),
    )
}.getOrNull()

private fun GeometryRecord.toDomainOrNull(): EarthEventGeometry? {
    val shape = point?.toCoordinateOrNull()?.let(EarthEventShape::Point)
        ?: rings?.map { ring -> ring.mapNotNull { it.toCoordinateOrNull() } }
            ?.takeIf { it.isNotEmpty() && it.first().isNotEmpty() }
            ?.let(EarthEventShape::Polygon)
        ?: return null
    val magnitude = if (magnitude != null && unit != null) EventMagnitude(magnitude, unit) else null
    return EarthEventGeometry(Instant.ofEpochMilli(date), shape, magnitude)
}

private fun List<Double>.toCoordinateOrNull(): GeoCoordinate? =
    if (size == 2) GeoCoordinate(latitude = this[0], longitude = this[1]) else null

private const val MIN_RING_POINTS = 3
