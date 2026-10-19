package com.radustavila.nasaexplorer.data.nasa.mapper

import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.ApodLink
import com.radustavila.nasaexplorer.core.model.MediaType
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ExplanationLinkDto
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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

    val plainExplanation = explanation?.takeIf(String::isNotBlank).orEmpty()

    return ApodEntity(
        date = parsedDate.toString(),
        title = title?.takeIf(String::isNotBlank) ?: UNTITLED,
        explanation = plainExplanation,
        mediaType = MediaType.toApiValue(MediaType.fromApiValue(mediaType)),
        url = mediaUrl,
        hdUrl = hdUrl.orNullIfBlank(),
        thumbnailUrl = thumbnailUrl.orNullIfBlank(),
        // NASA embeds newlines in credits; collapse them so the UI can lay the text out.
        copyright = copyright.orNullIfBlank()?.replace(WHITESPACE_RUN, " "),
        cachedAtEpochMillis = cachedAt.toEpochMilli(),
        lastSyncedAtEpochMillis = cachedAt.toEpochMilli(),
        isInFeed = isInFeed,
        permalink = permalink.orNullIfBlank(),
        alt = alt.orNullIfBlank(),
        explanationLinks = encodeLinks(
            explanationLinks.filter { link ->
                link.start in 0 until link.end && link.end <= plainExplanation.length
            },
        ),
    )
}

fun List<ApodDto>.toEntities(cachedAt: Instant, isInFeed: Boolean = true): List<ApodEntity> =
    mapNotNull { dto -> dto.toEntityOrNull(cachedAt = cachedAt, isInFeed = isInFeed) }
        // NASA occasionally repeats a date inside a window; keep the last occurrence so the
        // transactional upsert never receives conflicting rows.
        .associateBy(ApodEntity::date)
        .values
        .toList()

/**
 * [Apod.isFavorite] is always `false` here: favourites live in their own table, and the repository
 * marks them on the way out.
 */
fun ApodEntity.toDomain(): Apod = Apod(
    date = LocalDate.parse(date),
    title = title,
    explanation = explanation,
    mediaType = MediaType.fromApiValue(mediaType),
    url = url,
    hdUrl = hdUrl,
    thumbnailUrl = thumbnailUrl,
    copyright = copyright,
    permalink = permalink,
    altText = alt,
    explanationLinks = decodeLinks(explanationLinks)
        .filter { link -> link.end <= explanation.length }
        .map { link -> ApodLink(start = link.start, end = link.end, url = link.url) },
    cachedAt = Instant.ofEpochMilli(cachedAtEpochMillis),
    lastSyncedAt = Instant.ofEpochMilli(lastSyncedAtEpochMillis),
)

fun List<ApodEntity>.toDomain(): List<Apod> = map(ApodEntity::toDomain)

/**
 * Explanation links are stored as one JSON column: they are only ever read together with their row,
 * so a table of their own would add a join and nothing else.
 */
private val linksSerializer = ListSerializer(ExplanationLinkDto.serializer())
private val linksJson = Json { ignoreUnknownKeys = true }

private fun encodeLinks(links: List<ExplanationLinkDto>): String? =
    links.takeIf { it.isNotEmpty() }?.let { linksJson.encodeToString(linksSerializer, it) }

/** A column that does not parse is treated as "no links": the text itself is still intact. */
private fun decodeLinks(raw: String?): List<ExplanationLinkDto> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        linksJson.decodeFromString(linksSerializer, raw)
    } catch (_: IllegalArgumentException) {
        // Includes SerializationException.
        emptyList()
    }
}

private val WHITESPACE_RUN = Regex("\\s+")

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf(String::isNotBlank)

private const val UNTITLED = "Untitled"
