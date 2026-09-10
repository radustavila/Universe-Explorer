package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * Astronomy Picture of the Day, as the rest of the application understands it.
 *
 * The publication [date] is the identity of an entry: NASA publishes exactly one picture per day and
 * exposes no other stable identifier, so it is also the primary key in the database.
 */
data class Apod(
    val date: LocalDate,
    val title: String,
    val explanation: String,
    val mediaType: MediaType,
    val url: String,
    val hdUrl: String? = null,
    val thumbnailUrl: String? = null,
    val copyright: String? = null,
    val isFavorite: Boolean = false,
    val cachedAt: Instant = Instant.EPOCH,
) {
    /** Best available still image: the picture itself, or the video thumbnail NASA provides. */
    val previewImageUrl: String?
        get() = when (mediaType) {
            MediaType.IMAGE -> url
            MediaType.VIDEO, MediaType.OTHER -> thumbnailUrl
        }

    /** Highest quality still image for the details screen. */
    val fullImageUrl: String?
        get() = when (mediaType) {
            MediaType.IMAGE -> hdUrl ?: url
            MediaType.VIDEO, MediaType.OTHER -> thumbnailUrl
        }

    val isPlayable: Boolean get() = mediaType == MediaType.VIDEO

    companion object {
        /** The first Astronomy Picture of the Day; there is nothing to load before it. */
        val FIRST_PUBLICATION_DATE: LocalDate = LocalDate.of(1995, 6, 16)
    }
}
