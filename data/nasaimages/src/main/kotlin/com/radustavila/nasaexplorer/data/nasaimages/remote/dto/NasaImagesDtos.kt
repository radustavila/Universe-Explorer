package com.radustavila.nasaexplorer.data.nasaimages.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The library answers in Collection+JSON. Every field is optional here: the archive spans decades
 * of uploads and older items omit keywords, renditions or dates. Mapping decides what is usable.
 */

/** `GET /search`. */
@Serializable
data class SearchResponseDto(
    val collection: SearchCollectionDto = SearchCollectionDto(),
)

@Serializable
data class SearchCollectionDto(
    val items: List<SearchItemDto> = emptyList(),
    val metadata: SearchMetadataDto? = null,
    /** Paging links; `rel = "next"` is present while another page exists. */
    val links: List<PageLinkDto> = emptyList(),
)

@Serializable
data class SearchMetadataDto(
    @SerialName("total_hits") val totalHits: Int = 0,
)

@Serializable
data class PageLinkDto(
    val rel: String? = null,
    val href: String? = null,
)

@Serializable
data class SearchItemDto(
    /** The item's own collection.json; not used. */
    val href: String? = null,
    /** Always one entry in practice. */
    val data: List<SearchItemDataDto> = emptyList(),
    /** Image renditions (`~thumb`, `~small`, `~medium`, `~large`, `~orig`) and captions. */
    val links: List<RenditionLinkDto> = emptyList(),
)

@Serializable
data class SearchItemDataDto(
    @SerialName("nasa_id") val nasaId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("description_508") val description508: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("date_created") val dateCreated: String? = null,
    val center: String? = null,
    val keywords: List<String> = emptyList(),
    val photographer: String? = null,
    @SerialName("secondary_creator") val secondaryCreator: String? = null,
)

@Serializable
data class RenditionLinkDto(
    val href: String? = null,
    /** `preview` (the thumb), `alternate`, `canonical` (the original) or `captions`. */
    val rel: String? = null,
    val render: String? = null,
    /** Bytes. */
    val size: Long? = null,
)

/** `GET /asset/{nasa_id}`: every file of the item, renditions of all kinds mixed together. */
@Serializable
data class AssetResponseDto(
    val collection: AssetCollectionDto = AssetCollectionDto(),
)

@Serializable
data class AssetCollectionDto(
    val items: List<AssetItemDto> = emptyList(),
)

@Serializable
data class AssetItemDto(
    val href: String? = null,
)
