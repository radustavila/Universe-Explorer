package com.radustavila.nasaexplorer.data.launches.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Launch Library 2 (`/2.3.0/astronauts/?in_space=true&mode=list`) wire format, reduced to what the
 * crew list shows. Every field is optional, as for launches.
 */
@Serializable
data class AstronautListDto(
    val count: Int? = null,
    val results: List<AstronautDto> = emptyList(),
)

@Serializable
data class AstronautDto(
    val id: Int? = null,
    val name: String? = null,
    /** "Government", "Commercial", ... and "Non-Human" for Starman, the mannequin on a Tesla. */
    val type: AstronautTypeDto? = null,
    @SerialName("in_space") val inSpace: Boolean? = null,
    val agency: AstronautAgencyDto? = null,
    val image: LaunchImageDto? = null,
    val nationality: List<NationalityDto> = emptyList(),
    /** Launch of the current flight for someone in space, ISO-8601. */
    @SerialName("last_flight") val lastFlight: String? = null,
)

@Serializable
data class AstronautTypeDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class AstronautAgencyDto(
    val name: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class NationalityDto(
    val name: String? = null,
)
