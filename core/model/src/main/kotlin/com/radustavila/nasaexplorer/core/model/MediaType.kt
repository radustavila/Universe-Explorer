package com.radustavila.nasaexplorer.core.model

/**
 * APOD entries are not always images: NASA also publishes videos (usually YouTube/Vimeo embeds) and
 * occasionally interactive pages. The media type decides how an entry can be rendered.
 */
enum class MediaType {
    IMAGE,
    VIDEO,
    OTHER,
    ;

    companion object {
        fun fromApiValue(value: String?): MediaType = when (value?.lowercase()) {
            "image" -> IMAGE
            "video" -> VIDEO
            else -> OTHER
        }

        fun toApiValue(mediaType: MediaType): String = mediaType.name.lowercase()
    }
}
