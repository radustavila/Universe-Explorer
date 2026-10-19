package com.radustavila.nasaexplorer.data.mars.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Curiosity: `GET api/v1/raw_image_items/`, a page of the raw-image catalogue. */
@Serializable
data class CuriosityPageDto(
    val items: List<CuriosityImageDto> = emptyList(),
)

@Serializable
data class CuriosityImageDto(
    /** e.g. `5048MH0009060011804545C00_DXXX`. */
    val imageid: String? = null,
    val sol: Int? = null,
    /** Camera code, e.g. `MAHLI`, `NAV_LEFT_B`. */
    val instrument: String? = null,
    /** e.g. "Sol 5048: Mars Hand Lens Imager (MAHLI)". */
    val title: String? = null,
    /** HTML: paragraphs separated by `<br />`. */
    val description: String? = null,
    val url: String? = null,
    @SerialName("https_url") val httpsUrl: String? = null,
    /** ISO-8601 instant, e.g. `2026-10-18T20:22:12.000Z`. */
    @SerialName("date_taken") val dateTaken: String? = null,
    @SerialName("image_credit") val imageCredit: String? = null,
    /** The feed lists each image's thumbnail as an item of its own. */
    @SerialName("is_thumbnail") val isThumbnail: Boolean = false,
)

/** Perseverance: `GET rss/api/?feed=raw_images&category=mars2020&feedtype=json`. */
@Serializable
data class PerseverancePageDto(
    val images: List<PerseveranceImageDto> = emptyList(),
)

@Serializable
data class PerseveranceImageDto(
    /** e.g. `NLF_1650_0812345678_123ECM_N0123456NCAM00500_01_295J`. */
    val imageid: String? = null,
    val sol: Int? = null,
    val camera: PerseveranceCameraDto? = null,
    /** e.g. "Mars Perseverance Sol 1650: Left Navigation Camera (Navcam)". */
    val title: String? = null,
    val caption: String? = null,
    val credit: String? = null,
    /** `Full`, `Subframe`, `Thumbnail`, ... */
    @SerialName("sample_type") val sampleType: String? = null,
    /** UTC without an offset, e.g. `2026-10-17T21:05:12.000`. */
    @SerialName("date_taken_utc") val dateTakenUtc: String? = null,
    @SerialName("image_files") val imageFiles: PerseveranceImageFilesDto? = null,
)

@Serializable
data class PerseveranceCameraDto(
    val instrument: String? = null,
)

/** Renditions of one image: JPEGs 320, 800 and 1200 px wide, and the full-resolution PNG. */
@Serializable
data class PerseveranceImageFilesDto(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    @SerialName("full_res") val fullRes: String? = null,
)
