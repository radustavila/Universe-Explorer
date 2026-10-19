package com.radustavila.nasaexplorer.data.mars.mapper

import com.radustavila.nasaexplorer.core.model.MarsPhoto
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.data.mars.local.MarsPhotoEntity
import com.radustavila.nasaexplorer.data.mars.remote.dto.CuriosityImageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseveranceImageDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Curiosity's feed lists every thumbnail as an item of its own; those, and entries missing an id, a
 * sol or a picture, are dropped.
 */
internal fun CuriosityImageDto.toDomainOrNull(): MarsPhoto? {
    if (isThumbnail) return null
    val id = imageid?.takeIf(String::isNotBlank) ?: return null
    val sol = sol ?: return null
    val imageUrl = (httpsUrl ?: url)?.takeIf(String::isNotBlank)?.toHttps() ?: return null
    val cameraId = instrument?.takeIf(String::isNotBlank) ?: return null
    return MarsPhoto(
        id = id,
        rover = MarsRover.CURIOSITY,
        sol = sol,
        cameraId = cameraId,
        cameraName = title.cameraNameOr(cameraId),
        takenAt = dateTaken?.toInstantOrNull(),
        imageUrl = imageUrl,
        description = description?.htmlToText()?.takeIf(String::isNotEmpty),
        credit = imageCredit?.takeIf(String::isNotBlank),
    )
}

/** Thumbnails, and entries missing an id, a sol or a picture, are dropped. */
internal fun PerseveranceImageDto.toDomainOrNull(): MarsPhoto? {
    if (sampleType.equals(SAMPLE_TYPE_THUMBNAIL, ignoreCase = true)) return null
    val id = imageid?.takeIf(String::isNotBlank) ?: return null
    val sol = sol ?: return null
    val files = imageFiles ?: return null
    // The 1200 px JPEG rather than the full-resolution PNG, which is several times its size.
    val imageUrl = listOfNotNull(files.large, files.fullRes, files.medium)
        .firstOrNull(String::isNotBlank)?.toHttps() ?: return null
    val cameraId = camera?.instrument?.takeIf(String::isNotBlank) ?: return null
    return MarsPhoto(
        id = id,
        rover = MarsRover.PERSEVERANCE,
        sol = sol,
        cameraId = cameraId,
        cameraName = title.cameraNameOr(cameraId),
        takenAt = dateTakenUtc?.toInstantOrNull(),
        imageUrl = imageUrl,
        thumbnailUrl = listOfNotNull(files.small, files.medium).firstOrNull(String::isNotBlank)?.toHttps(),
        description = caption?.htmlToText()?.takeIf(String::isNotEmpty),
        credit = credit?.takeIf(String::isNotBlank),
    )
}

internal fun MarsPhoto.toEntity(cachedAt: Instant) = MarsPhotoEntity(
    id = id,
    rover = rover.key,
    sol = sol,
    cameraId = cameraId,
    cameraName = cameraName,
    takenAt = takenAt?.toEpochMilli(),
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    description = description,
    credit = credit,
    cachedAt = cachedAt.toEpochMilli(),
)

/** Null for a row of a rover this version does not know. */
internal fun MarsPhotoEntity.toDomain(): MarsPhoto? {
    val rover = MarsRover.fromKey(rover) ?: return null
    return MarsPhoto(
        id = id,
        rover = rover,
        sol = sol,
        cameraId = cameraId,
        cameraName = cameraName,
        takenAt = takenAt?.let(Instant::ofEpochMilli),
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        description = description,
        credit = credit,
    )
}

/** "Sol 5048: Mars Hand Lens Imager (MAHLI)" → "Mars Hand Lens Imager (MAHLI)". */
private fun String?.cameraNameOr(cameraId: String): String =
    this?.substringAfter(": ", missingDelimiterValue = "")?.trim()?.takeIf(String::isNotEmpty)
        ?: cameraId.replace('_', ' ')

/** Curiosity's instants end in `Z`; Perseverance's are UTC without an offset. */
private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    try {
        LocalDateTime.parse(this).toInstant(ZoneOffset.UTC)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun String.toHttps(): String = if (startsWith("http://")) "https://" + removePrefix("http://") else this

/** The feeds' captions are HTML fragments: line breaks become newlines, other tags go. */
internal fun String.htmlToText(): String = this
    .replace(LINE_BREAK, "\n")
    .replace(TAG, "")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .replace(BLANK_LINES, "\n\n")
    .trim()

private const val SAMPLE_TYPE_THUMBNAIL = "Thumbnail"
private val LINE_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]+>")
private val BLANK_LINES = Regex("""\n\s*\n+""")
