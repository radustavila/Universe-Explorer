package com.radustavila.nasaexplorer.data.nasa.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of a NASA APOD entry.
 *
 * Kept deliberately permissive: archive entries are inconsistent (missing `hdurl`, missing
 * `copyright`, occasional `media_type` values other than image/video), and a single malformed entry
 * must not fail an entire window. Defaults plus `ignoreUnknownKeys` handle that; entries that cannot
 * even be dated are dropped during mapping.
 */
@Serializable
data class ApodDto(
    @SerialName("date") val date: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("explanation") val explanation: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("hdurl") val hdUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("service_version") val serviceVersion: String? = null,
    @SerialName("permalink") val permalink: String? = null,
    @SerialName("alt") val alt: String? = null,
    /** Links inside [explanation], as character ranges of the plain text. */
    @SerialName("explanation_links") val explanationLinks: List<ExplanationLinkDto> = emptyList(),
)

/** A link in an explanation: the plain-text range `[start, end)` and where it points. */
@Serializable
data class ExplanationLinkDto(
    @SerialName("start") val start: Int,
    @SerialName("end") val end: Int,
    @SerialName("url") val url: String,
)
