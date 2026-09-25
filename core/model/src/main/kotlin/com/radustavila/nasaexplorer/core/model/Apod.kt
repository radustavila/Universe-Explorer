package com.radustavila.nasaexplorer.core.model

import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    /** When this entry first entered the local cache. */
    val cachedAt: Instant = Instant.EPOCH,
    /** When this entry was last confirmed against the API, i.e. how stale the cached copy is. */
    val lastSyncedAt: Instant = Instant.EPOCH,
) {
    /**
     * Best available still image: the picture itself, the video thumbnail NASA provides, or - when
     * it provides none - a frame recovered from the video itself (see [derivedVideoPreviewUrl]).
     */
    val previewImageUrl: String?
        get() = when (mediaType) {
            MediaType.IMAGE -> url
            MediaType.VIDEO -> thumbnailUrl ?: derivedVideoPreviewUrl
            MediaType.OTHER -> thumbnailUrl
        }

    /**
     * Still frame for a video NASA published with `thumbnail_url: ""`, which is common in the
     * archive: YouTube serves a poster frame for every video at a predictable address, and a file
     * hosted by NASA carries its own first frame, which the image loader decodes out of the stream.
     * Anything else (Vimeo, dead embeds) has no still frame to show.
     */
    private val derivedVideoPreviewUrl: String?
        get() = youTubeThumbnailUrl(url) ?: streamUrl?.takeIf(::hasDecodableFirstFrame)

    /** Highest quality still image for the details screen. */
    val fullImageUrl: String?
        get() = when (mediaType) {
            MediaType.IMAGE -> hdUrl ?: url
            MediaType.VIDEO, MediaType.OTHER -> thumbnailUrl
        }

    val isPlayable: Boolean get() = mediaType == MediaType.VIDEO

    /**
     * The page NASA publishes for this entry. The archive is addressed by date in `apYYMMDD.html`
     * form, so no identifier beyond [date] is needed.
     */
    val webPageUrl: String get() = "$ARCHIVE_BASE_URL/ap${ARCHIVE_DATE_FORMAT.format(date)}.html"

    /**
     * The media stream a player can open directly, or `null` when there is none.
     *
     * Most APOD videos are YouTube or Vimeo *embed pages*, which are HTML documents rather than
     * media streams and therefore cannot be decoded in-app; a minority are plain files or
     * adaptive manifests hosted by NASA, and those play natively.
     */
    val streamUrl: String?
        get() = url.takeIf { mediaType == MediaType.VIDEO && isDirectMediaStream(it) }

    companion object {
        private const val ARCHIVE_BASE_URL = "https://apod.nasa.gov/apod"
        private val ARCHIVE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")

        /** The first Astronomy Picture of the Day; there is nothing to load before it. */
        val FIRST_PUBLICATION_DATE: LocalDate = LocalDate.of(1995, 6, 16)

        /** Single-file containers, from which a still frame can also be decoded. */
        private val PROGRESSIVE_EXTENSIONS = setOf("mp4", "m4v", "webm", "mkv", "mov", "ogv")

        /** Adaptive manifests: ExoPlayer plays them, but a frame extractor cannot read them. */
        private val MANIFEST_EXTENSIONS = setOf("m3u8", "mpd")

        private val YOUTUBE_HOSTS = setOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
        )

        /** YouTube ids are 11 characters of the URL-safe alphabet. */
        private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

        /** Path prefixes under which a YouTube URL carries the id as its last segment. */
        private val YOUTUBE_ID_PATHS = setOf("embed", "v", "shorts", "live")

        /**
         * True when [url] is media a player can open directly rather than a web page.
         */
        private fun isDirectMediaStream(url: String): Boolean =
            extensionOf(url) in PROGRESSIVE_EXTENSIONS + MANIFEST_EXTENSIONS

        /**
         * True when a still frame can be pulled out of [url] itself. Only whole-file containers
         * qualify: a frame extractor cannot follow an adaptive manifest to its segments.
         */
        fun hasDecodableFirstFrame(url: String): Boolean =
            extensionOf(url) in PROGRESSIVE_EXTENSIONS

        private fun extensionOf(url: String): String = url
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()

        /**
         * The poster frame YouTube generates for an embed, or `null` when [url] is not YouTube.
         * `hqdefault` is used because it is the largest size that exists for every video.
         */
        fun youTubeThumbnailUrl(url: String): String? =
            youTubeVideoId(url)?.let { id -> "https://img.youtube.com/vi/$id/hqdefault.jpg" }

        private fun youTubeVideoId(url: String): String? {
            val uri = try {
                URI(url)
            } catch (_: URISyntaxException) {
                return null
            }

            val host = uri.host?.removePrefix("www.")?.removePrefix("m.")?.lowercase()
            if (host !in YOUTUBE_HOSTS) return null

            val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
            val candidate = when {
                // youtu.be/<id>
                host == "youtu.be" -> segments.firstOrNull()
                // youtube.com/embed/<id>, /v/<id>, /shorts/<id>, /live/<id>
                segments.size >= 2 && segments[segments.size - 2] in YOUTUBE_ID_PATHS ->
                    segments.last()
                // youtube.com/watch?v=<id>
                else -> uri.query?.split('&')
                    ?.firstOrNull { it.startsWith("v=") }
                    ?.removePrefix("v=")
            }

            return candidate?.takeIf { YOUTUBE_ID.matches(it) }
        }
    }
}
