package com.radustavila.nasaexplorer.data.launches.mapper

import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.data.launches.local.LaunchEntity
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * DTO to entity. Returns `null` for an entry the card cannot show (no id, name, page or a target
 * time that does not parse), so one bad entry never fails the whole refresh.
 */
fun LaunchDto.toEntityOrNull(): LaunchEntity? {
    val id = id?.takeIf(String::isNotBlank) ?: return null
    val name = name?.takeIf(String::isNotBlank) ?: return null
    val pageUrl = url?.takeIf(String::isNotBlank) ?: return null
    val net = net?.toInstantOrNull() ?: return null

    return LaunchEntity(
        id = id,
        name = name.trim(),
        netEpochMillis = net.toEpochMilli(),
        statusId = LaunchStatus.fromId(status?.id).id,
        rocket = rocket?.configuration?.let { it.fullName ?: it.name }?.trim().orEmpty(),
        pad = listOfNotNull(pad?.name, pad?.location?.name)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = ", "),
        imageUrl = (image?.thumbnailUrl ?: image?.imageUrl)?.takeIf(String::isNotBlank),
        // LL2 lists the streams in its own priority order; the first one is the official webcast.
        livestreamUrl = vidUrls.firstNotNullOfOrNull { video -> video.url?.takeIf(String::isNotBlank) },
        pageUrl = pageUrl,
    )
}

fun LaunchEntity.toDomain(): Launch = Launch(
    id = id,
    name = name,
    net = Instant.ofEpochMilli(netEpochMillis),
    status = LaunchStatus.fromId(statusId),
    rocket = rocket,
    pad = pad,
    imageUrl = imageUrl,
    livestreamUrl = livestreamUrl,
    pageUrl = pageUrl,
)

private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    null
}
