package com.radustavila.nasaexplorer.data.launches.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Launch Library 2 (`/2.3.0/launches/{upcoming,previous}/?mode=detailed`, `/launches/{id}/`) wire
 * format, reduced to the fields the card and the Launches screen use. Every field is optional: a
 * malformed entry is dropped during mapping instead of failing the whole response.
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
    @SerialName("launch_service_provider") val launchServiceProvider: AgencyDto? = null,
    /** Only present in `detailed` mode, which is why the app asks for it. */
    @SerialName("vid_urls") val vidUrls: List<VidUrlDto> = emptyList(),
    @SerialName("net_precision") val netPrecision: NetPrecisionDto? = null,
    @SerialName("window_start") val windowStart: String? = null,
    @SerialName("window_end") val windowEnd: String? = null,
    /** Weather go probability in percent; -1 or null when there is no forecast. */
    val probability: Int? = null,
    @SerialName("failreason") val failReason: String? = null,
    val mission: MissionDto? = null,
)

@Serializable
data class NetPrecisionDto(
    val id: Int? = null,
    val abbrev: String? = null,
)

@Serializable
data class MissionDto(
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
    val orbit: OrbitDto? = null,
)

@Serializable
data class OrbitDto(
    val name: String? = null,
    val abbrev: String? = null,
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
    /** First stages and boosters, with their landings (detailed mode). */
    @SerialName("launcher_stage") val launcherStages: List<LauncherStageDto> = emptyList(),
    /** Crewed vehicles; their `launch_crew` says who flies (detailed mode). */
    @SerialName("spacecraft_stage") val spacecraftStages: List<SpacecraftStageDto> = emptyList(),
)

@Serializable
data class RocketConfigurationDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val variant: String? = null,
)

@Serializable
data class LauncherStageDto(
    val type: String? = null,
    @SerialName("launcher_flight_number") val launcherFlightNumber: Int? = null,
    val launcher: LauncherDto? = null,
    val landing: LandingDto? = null,
)

@Serializable
data class LauncherDto(
    @SerialName("serial_number") val serialNumber: String? = null,
)

@Serializable
data class LandingDto(
    val attempt: Boolean? = null,
    val success: Boolean? = null,
    @SerialName("landing_location") val landingLocation: LandingLocationDto? = null,
    val type: LandingTypeDto? = null,
)

@Serializable
data class LandingLocationDto(
    val name: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class LandingTypeDto(
    val name: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class SpacecraftStageDto(
    @SerialName("launch_crew") val launchCrew: List<CrewMemberDto> = emptyList(),
)

/** Only counted: who flies is the crew list's business (Sky tonight). */
@Serializable
data class CrewMemberDto(
    val id: Int? = null,
)

@Serializable
data class PadDto(
    val name: String? = null,
    val location: PadLocationDto? = null,
    /** Numbers in 2.3.0 (strings in older versions, which this client never asks for). */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class PadLocationDto(
    val name: String? = null,
)

@Serializable
data class AgencyDto(
    val name: String? = null,
    val abbrev: String? = null,
)

@Serializable
data class VidUrlDto(
    val url: String? = null,
    val priority: Int? = null,
    val title: String? = null,
    val publisher: String? = null,
    val live: Boolean? = null,
)
