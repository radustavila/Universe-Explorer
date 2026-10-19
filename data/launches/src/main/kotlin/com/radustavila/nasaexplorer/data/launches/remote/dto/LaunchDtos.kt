package com.radustavila.nasaexplorer.data.launches.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Launch Library 2 (`/2.3.0/launches/upcoming/?mode=detailed`) wire format, reduced to the fields
 * the next-launches card uses. Every field is optional: a malformed entry is dropped during mapping
 * instead of failing the whole response.
 */
@Serializable
data class LaunchListDto(
    val count: Int? = null,
    val results: List<LaunchDto> = emptyList(),
)

@Serializable
data class LaunchDto(
    val id: String? = null,
    /** API URL of the launch. */
    val url: String? = null,
    /** URL-safe name; Space Launch Now's public launch pages are keyed by it. */
    val slug: String? = null,
    val name: String? = null,
    /** ISO-8601 UTC instant, e.g. `2026-10-19T10:30:00Z`. */
    val net: String? = null,
    val status: LaunchStatusDto? = null,
    val image: LaunchImageDto? = null,
    val rocket: RocketDto? = null,
    val pad: PadDto? = null,
    /** Only present in `detailed` mode, which is why the app asks for it. */
    @SerialName("vid_urls") val vidUrls: List<VidUrlDto> = emptyList(),
)

@Serializable
data class LaunchStatusDto(
    val id: Int? = null,
    val name: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class LaunchImageDto(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable
data class RocketDto(
    val configuration: RocketConfigurationDto? = null,
)

@Serializable
data class RocketConfigurationDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
data class PadDto(
    val name: String? = null,
    val location: PadLocationDto? = null,
)

@Serializable
data class PadLocationDto(
    val name: String? = null,
)

@Serializable
data class VidUrlDto(
    val url: String? = null,
    val priority: Int? = null,
)
