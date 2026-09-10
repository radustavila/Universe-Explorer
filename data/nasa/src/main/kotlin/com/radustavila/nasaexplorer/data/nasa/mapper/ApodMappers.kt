package com.radustavila.nasaexplorer.data.nasa.mapper

import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.MediaType
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * DTO -> entity -> domain mapping. Both boundaries are crossed here and nowhere else: DTOs never
 * reach the domain layer and entities never reach the UI.
 */

/**
 * @return `null` when the entry cannot be identified (no parsable date) or has no media URL, i.e.
 * when it could not be rendered or de-duplicated anyway.
 */
fun ApodDto.toEntityOrNull(cachedAt: Instant): ApodEntity? {
    val parsedDate = date?.let { raw ->
        try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: return null

    val mediaUrl = url ?: hdUrl ?: thumbnailUrl ?: return null

    return ApodEntity(
        date = parsedDate.toString(),
        title = title?.takeIf(String::isNotBlank) ?: UNTITLED,
        explanation = explanation?.takeIf(String::isNotBlank).orEmpty(),
        mediaType = MediaType.toApiValue(MediaType.fromApiValue(mediaType)),
        url = mediaUrl,
        hdUrl = hdUrl,
        thumbnailUrl = thumbnailUrl,
        copyright = copyright?.trim()?.takeIf(String::isNotBlank),
        cachedAtEpochMillis = cachedAt.toEpochMilli(),
    )
}

fun List<ApodDto>.toEntities(cachedAt: Instant): List<ApodEntity> =
    mapNotNull { dto -> dto.toEntityOrNull(cachedAt) }
        // NASA occasionally repeats a date inside a window; keep the last occurrence so the
        // transactional upsert never receives conflicting rows.
        .associateBy(ApodEntity::date)
        .values
        .toList()

fun ApodEntity.toDomain(): Apod = Apod(
    date = LocalDate.parse(date),
    title = title,
    explanation = explanation,
    mediaType = MediaType.fromApiValue(mediaType),
    url = url,
    hdUrl = hdUrl,
    thumbnailUrl = thumbnailUrl,
    copyright = copyright,
    isFavorite = isFavorite,
    cachedAt = Instant.ofEpochMilli(cachedAtEpochMillis),
)

fun List<ApodEntity>.toDomain(): List<Apod> = map(ApodEntity::toDomain)

private const val UNTITLED = "Untitled"
