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
 * @param isInFeed whether the entry belongs to the contiguous window the feed pages through; a day
 * fetched on its own for the details screen does not.
 * @return `null` when the entry cannot be identified (no parsable date) or has no media URL, i.e.
 * when it could not be rendered or de-duplicated anyway.
 */
fun ApodDto.toEntityOrNull(cachedAt: Instant, isInFeed: Boolean = true): ApodEntity? {
    val parsedDate = date?.let { raw ->
        try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: return null

    // NASA sends "" rather than omitting the field for videos it has no still frame for.
    val mediaUrl = url.orNullIfBlank() ?: hdUrl.orNullIfBlank() ?: thumbnailUrl.orNullIfBlank()
        ?: return null

    return ApodEntity(
        date = parsedDate.toString(),
        title = title?.takeIf(String::isNotBlank) ?: UNTITLED,
        explanation = explanation?.takeIf(String::isNotBlank).orEmpty(),
        mediaType = MediaType.toApiValue(MediaType.fromApiValue(mediaType)),
        url = mediaUrl,
        hdUrl = hdUrl.orNullIfBlank(),
        thumbnailUrl = thumbnailUrl.orNullIfBlank(),
        // NASA embeds newlines in credits; collapse them so the UI can lay the text out.
        copyright = copyright.orNullIfBlank()?.replace(WHITESPACE_RUN, " "),
        cachedAtEpochMillis = cachedAt.toEpochMilli(),
        lastSyncedAtEpochMillis = cachedAt.toEpochMilli(),
        isInFeed = isInFeed,
    )
}

fun List<ApodDto>.toEntities(cachedAt: Instant, isInFeed: Boolean = true): List<ApodEntity> =
    mapNotNull { dto -> dto.toEntityOrNull(cachedAt = cachedAt, isInFeed = isInFeed) }
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
    lastSyncedAt = Instant.ofEpochMilli(lastSyncedAtEpochMillis),
)

fun List<ApodEntity>.toDomain(): List<Apod> = map(ApodEntity::toDomain)

private val WHITESPACE_RUN = Regex("\\s+")

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf(String::isNotBlank)

private const val UNTITLED = "Untitled"
