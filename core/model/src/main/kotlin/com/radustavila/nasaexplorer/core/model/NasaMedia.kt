package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate

/**
 * One item of the NASA Image and Video Library (images-api.nasa.gov): a picture, a video or an
 * audio recording, identified by the library's [nasaId].
 *
 * The image fields are renditions of the same item, smallest first. Videos carry a still frame in
 * them; audio usually has none. The playable file of a video or recording is not part of the item:
 * it is resolved from the item's asset list when it is about to be played.
 */
data class NasaMedia(
    val nasaId: String,
    val title: String,
    val description: String,
    val mediaType: NasaMediaType,
    val dateCreated: LocalDate? = null,
    val center: String? = null,
    /** Photographer or credited creator, when the library names one. */
    val creator: String? = null,
    val keywords: List<String> = emptyList(),
    /** Small rendition for rails and grids (`~thumb`). */
    val thumbnailUrl: String? = null,
    /** Screen-sized rendition (`~large`, else `~medium`). */
    val imageUrl: String? = null,
    /** Largest rendition an image decoder can draw (`~orig` unless it is e.g. a TIFF), for zooming. */
    val fullImageUrl: String? = null,
    /** The original file as published, whatever its format, for downloading. */
    val originalUrl: String? = null,
    /** Short accessible description (the library's `description_508`). */
    val altText: String? = null,
) {
    /** Best image to stand for the item in a list, a favourite snapshot or a player poster. */
    val previewUrl: String? get() = imageUrl ?: thumbnailUrl

    val isPlayable: Boolean get() = mediaType != NasaMediaType.IMAGE
}

enum class NasaMediaType(val apiValue: String) {
    IMAGE("image"),
    VIDEO("video"),
    AUDIO("audio"),
    ;

    companion object {
        fun fromApiValue(value: String?): NasaMediaType? =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) }
    }
}

/**
 * The curated rails at the top of the Universe tab, top to bottom. Each is a fixed library query
 * (owned by the data layer), cached so the tab opens offline.
 *
 * [key] is persisted: never rename or reuse one.
 */
enum class GalleryRail(val key: String) {
    WEBB_GALAXIES("webb_galaxies"),
    HUBBLE_GALAXIES("hubble_galaxies"),
    NEBULAE("nebulae"),
    BLACK_HOLES("black_holes"),
    MILKY_WAY("milky_way"),
    WEBB("webb"),
    ;

    companion object {
        fun fromKey(key: String): GalleryRail? = entries.firstOrNull { it.key == key }
    }
}

/** A full-text query over the whole library. */
data class NasaMediaQuery(
    val text: String,
    val mediaTypes: Set<NasaMediaType> = NasaMediaType.entries.toSet(),
    val yearStart: Int? = null,
    val yearEnd: Int? = null,
)

/** One page of search results; [nextPage] is null on the last page the library will serve. */
data class NasaMediaPage(
    val items: List<NasaMedia>,
    val totalHits: Int,
    val nextPage: Int?,
)
