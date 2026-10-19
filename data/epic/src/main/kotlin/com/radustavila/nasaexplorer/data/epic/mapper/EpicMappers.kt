package com.radustavila.nasaexplorer.data.epic.mapper

import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.GeoPoint
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicDateDto
import com.radustavila.nasaexplorer.data.epic.remote.dto.EpicImageDto
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * DTO -> entity -> domain mapping for EPIC. Archive URLs are derived here, the only place that
 * knows how EPIC lays out its file tree:
 * `archive/{collection}/{yyyy}/{MM}/{dd}/{thumbs|jpg}/{image}.jpg`.
 */

/**
 * @return `null` for a record that cannot be placed (no parsable capture time), has no file to
 * show, or has no centroid to label it with.
 */
fun EpicImageDto.toEntityOrNull(
    collection: EpicCollection,
    cachedAt: Instant,
    archiveBaseUrl: String,
): EpicImageEntity? {
    val capturedAt = date?.let(::parseCaptureTime) ?: return null
    val imageName = image.orNullIfBlank() ?: return null
    val latitude = centroidCoordinates?.lat ?: return null
    val longitude = centroidCoordinates.lon ?: return null

    val day = capturedAt.atOffset(ZoneOffset.UTC).toLocalDate()
    val directory = "${archiveBaseUrl}archive/${collection.apiValue}/${ARCHIVE_PATH_FORMAT.format(day)}"

    return EpicImageEntity(
        collection = collection.apiValue,
        // The identifier is the capture timestamp; the file name carries it too, as a fallback.
        identifier = identifier.orNullIfBlank() ?: imageName,
        date = day.toString(),
        capturedAtEpochMillis = capturedAt.toEpochMilli(),
        caption = caption.orNullIfBlank().orEmpty(),
        centroidLatitude = latitude,
        centroidLongitude = longitude,
        thumbnailUrl = "$directory/thumbs/$imageName.jpg",
        imageUrl = "$directory/jpg/$imageName.jpg",
        cachedAtEpochMillis = cachedAt.toEpochMilli(),
    )
}

fun List<EpicImageDto>.toEntities(
    collection: EpicCollection,
    cachedAt: Instant,
    archiveBaseUrl: String,
): List<EpicImageEntity> =
    mapNotNull { dto -> dto.toEntityOrNull(collection, cachedAt, archiveBaseUrl) }
        // One row per key, or the replacing insert would silently keep whichever came last anyway.
        .associateBy(EpicImageEntity::identifier)
        .values
        .toList()

/** Parsable, de-duplicated days (the enhanced listing repeats some), newest first. */
fun List<EpicDateDto>.toDates(): List<LocalDate> =
    mapNotNull { dto -> dto.date?.trim()?.let(::parseDateOrNull) }
        .distinct()
        .sortedDescending()

fun EpicImageEntity.toDomain(): EpicImage = EpicImage(
    identifier = identifier,
    collection = EpicCollection.fromApiValue(collection) ?: EpicCollection.NATURAL,
    capturedAt = Instant.ofEpochMilli(capturedAtEpochMillis),
    caption = caption,
    centroid = GeoPoint(latitude = centroidLatitude, longitude = centroidLongitude),
    thumbnailUrl = thumbnailUrl,
    imageUrl = imageUrl,
)

fun List<EpicImageEntity>.toDomain(): List<EpicImage> = map(EpicImageEntity::toDomain)

/** EPIC capture times are UTC without an offset: `2026-10-16 00:22:24`. */
private fun parseCaptureTime(raw: String): Instant? = try {
    LocalDateTime.parse(raw.trim(), CAPTURE_TIME_FORMAT).toInstant(ZoneOffset.UTC)
} catch (_: DateTimeParseException) {
    null
}

private fun parseDateOrNull(raw: String): LocalDate? = try {
    LocalDate.parse(raw)
} catch (_: DateTimeParseException) {
    null
}

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf(String::isNotBlank)

private val CAPTURE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val ARCHIVE_PATH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
