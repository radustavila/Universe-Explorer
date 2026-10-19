package com.radustavila.nasaexplorer.data.launches.mapper

import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.data.launches.local.PersonInSpaceEntity
import com.radustavila.nasaexplorer.data.launches.remote.dto.AstronautDto
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * DTO to entity, or null for an entry the list should not show: no id or name, someone the API
 * says is not in space, or a non-human "astronaut" (Starman, launched on a Tesla in 2018, is still
 * listed as in space).
 */
fun AstronautDto.toEntityOrNull(): PersonInSpaceEntity? {
    val id = id ?: return null
    val name = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (inSpace == false) return null
    if (type?.id == NON_HUMAN_TYPE_ID || type?.name.equals(NON_HUMAN_TYPE_NAME, ignoreCase = true)) return null

    return PersonInSpaceEntity(
        id = id,
        name = name,
        agency = (agency?.abbrev ?: agency?.name)?.trim()?.takeIf(String::isNotEmpty),
        nationality = nationality.firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
        imageUrl = (image?.thumbnailUrl ?: image?.imageUrl)?.takeIf(String::isNotBlank),
        launchedEpochMillis = lastFlight?.let { flight ->
            try {
                Instant.parse(flight).toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        },
    )
}

fun PersonInSpaceEntity.toDomain(): PersonInSpace = PersonInSpace(
    id = id,
    name = name,
    agency = agency,
    nationality = nationality,
    imageUrl = imageUrl,
    launchedAt = launchedEpochMillis?.let(Instant::ofEpochMilli),
)

private const val NON_HUMAN_TYPE_ID = 6
private const val NON_HUMAN_TYPE_NAME = "Non-Human"
