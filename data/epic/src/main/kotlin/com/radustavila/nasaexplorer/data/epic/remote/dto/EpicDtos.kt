package com.radustavila.nasaexplorer.data.epic.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of one EPIC image record, reduced to the fields the app uses (spacecraft, Moon and Sun
 * positions are ignored via `ignoreUnknownKeys`).
 *
 * `date` is the capture time in UTC, formatted `yyyy-MM-dd HH:mm:ss`. `image` is the file name
 * without extension, the key into the archive's directory tree.
 */
@Serializable
data class EpicImageDto(
    @SerialName("identifier") val identifier: String? = null,
    @SerialName("caption") val caption: String? = null,
    @SerialName("image") val image: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("centroid_coordinates") val centroidCoordinates: EpicCoordinatesDto? = null,
)

@Serializable
data class EpicCoordinatesDto(
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
)

/** One entry of the `/all` listing. */
@Serializable
data class EpicDateDto(
    @SerialName("date") val date: String? = null,
)
