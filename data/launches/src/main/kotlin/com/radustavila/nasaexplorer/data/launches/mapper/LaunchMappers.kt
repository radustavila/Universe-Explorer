package com.radustavila.nasaexplorer.data.launches.mapper

import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.data.launches.local.LaunchEntity
import com.radustavila.nasaexplorer.data.launches.remote.dto.AgencyDto
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
    val pageUrl = pageUrl() ?: return null
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
        provider = launchServiceProvider?.displayName(),
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
    provider = provider,
    imageUrl = imageUrl,
    livestreamUrl = livestreamUrl,
    pageUrl = pageUrl,
)

/**
 * The provider as a widget line can hold it: its name, or its abbreviation when the name is long
 * ("China Aerospace Science and Technology Corporation" becomes "CASC").
 */
private fun AgencyDto.displayName(): String? {
    val name = name?.trim()?.takeIf(String::isNotEmpty)
    val abbrev = abbrev?.trim()?.takeIf(String::isNotEmpty)
    return if (name != null && name.length > MAX_PROVIDER_NAME_LENGTH && abbrev != null) abbrev else name ?: abbrev
}

private const val MAX_PROVIDER_NAME_LENGTH = 24

/**
 * A page for people: Space Launch Now, The Space Devs' own site on top of Launch Library 2. The API
 * URL is only a last resort - in a browser it shows the API's developer view, and every visit counts
 * against the same 15-calls-an-hour budget the app's refresh uses.
 */
private fun LaunchDto.pageUrl(): String? =
    slug?.takeIf(String::isNotBlank)?.let { "$SPACE_LAUNCH_NOW_LAUNCH_URL$it/" }
        ?: url?.takeIf(String::isNotBlank)

private const val SPACE_LAUNCH_NOW_LAUNCH_URL = "https://spacelaunchnow.app/launch/"

private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    null
}
