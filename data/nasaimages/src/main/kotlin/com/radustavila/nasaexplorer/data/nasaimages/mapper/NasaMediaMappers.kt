package com.radustavila.nasaexplorer.data.nasaimages.mapper

import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaPage
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaMediaEntity
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesRemoteDataSource
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.AssetResponseDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.RenditionLinkDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchItemDto
import com.radustavila.nasaexplorer.data.nasaimages.remote.dto.SearchResponseDto
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Items without an id, a known media type or a title are dropped. */
fun SearchItemDto.toDomainOrNull(): NasaMedia? {
    val data = data.firstOrNull() ?: return null
    val nasaId = data.nasaId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val mediaType = NasaMediaType.fromApiValue(data.mediaType) ?: return null
    val title = data.title?.cleanText()?.takeIf(String::isNotEmpty) ?: return null

    val renditions = Renditions.from(links)
    return NasaMedia(
        nasaId = nasaId,
        title = title,
        description = data.description?.cleanText().orEmpty(),
        mediaType = mediaType,
        dateCreated = data.dateCreated?.toLocalDateOrNull(),
        center = data.center?.trim()?.takeIf(String::isNotEmpty),
        creator = (data.secondaryCreator ?: data.photographer)?.trim()?.takeIf(String::isNotEmpty),
        keywords = data.keywords
            // Older uploads put every keyword into one comma-separated string.
            .flatMap { it.split(',') }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase),
        thumbnailUrl = renditions.thumb,
        imageUrl = renditions.large ?: renditions.medium ?: renditions.small ?: renditions.thumb,
        fullImageUrl = renditions.zoomable ?: renditions.large ?: renditions.medium,
        originalUrl = renditions.original?.href,
        altText = data.description508?.cleanText()?.takeIf(String::isNotEmpty),
    )
}

fun SearchResponseDto.toPage(page: Int): NasaMediaPage {
    val hasNext = collection.links.any { it.rel == "next" } &&
        page * NasaImagesRemoteDataSource.SEARCH_PAGE_SIZE < NasaImagesRemoteDataSource.MAX_RESULTS
    return NasaMediaPage(
        items = collection.items.mapNotNull(SearchItemDto::toDomainOrNull),
        totalHits = collection.metadata?.totalHits ?: 0,
        nextPage = if (hasNext) page + 1 else null,
    )
}

/**
 * The file to stream for a video or an audio item, or null when it has none. Phone-sized
 * renditions come first: the originals run to hundreds of megabytes.
 */
fun AssetResponseDto.playbackUrlFor(mediaType: NasaMediaType): String? {
    val files = collection.items.mapNotNull { it.href?.toHttpsUrlOrNull() }
    val (preferred, extensions) = when (mediaType) {
        NasaMediaType.VIDEO -> VIDEO_RENDITIONS to VIDEO_EXTENSIONS
        NasaMediaType.AUDIO -> AUDIO_RENDITIONS to AUDIO_EXTENSIONS
        NasaMediaType.IMAGE -> return null
    }
    val chosen = preferred.firstNotNullOfOrNull { suffix ->
        files.firstOrNull { it.fileName().endsWith(suffix, ignoreCase = true) }
    } ?: files.firstOrNull { file -> extensions.any { file.fileName().endsWith(it, ignoreCase = true) } }
    return chosen?.toString()
}

fun NasaMedia.toEntity(cachedAt: Instant): NasaMediaEntity = NasaMediaEntity(
    nasaId = nasaId,
    title = title,
    description = description,
    mediaType = mediaType.apiValue,
    dateCreated = dateCreated?.toString(),
    center = center,
    creator = creator,
    keywords = keywords.joinToString("\n"),
    thumbnailUrl = thumbnailUrl,
    imageUrl = imageUrl,
    fullImageUrl = fullImageUrl,
    originalUrl = originalUrl,
    altText = altText,
    cachedAt = cachedAt.toEpochMilli(),
)

fun NasaMediaEntity.toDomain(): NasaMedia = NasaMedia(
    nasaId = nasaId,
    title = title,
    description = description,
    mediaType = NasaMediaType.fromApiValue(mediaType) ?: NasaMediaType.IMAGE,
    dateCreated = dateCreated?.toLocalDateOrNull(),
    center = center,
    creator = creator,
    keywords = keywords.split('\n').filter(String::isNotEmpty),
    thumbnailUrl = thumbnailUrl,
    imageUrl = imageUrl,
    fullImageUrl = fullImageUrl,
    originalUrl = originalUrl,
    altText = altText,
)

/** An item's image renditions, recognised by the `~size` suffix of their file names. */
private data class Renditions(
    val thumb: String?,
    val small: String?,
    val medium: String?,
    val large: String?,
    val original: RenditionLinkDto?,
) {
    /**
     * The original, when it is something an image decoder can draw and small enough to fetch for
     * a closer look; many originals are TIFFs, or PNGs of tens of megabytes.
     */
    val zoomable: String?
        get() = original?.takeIf { link ->
            val path = link.href.orEmpty()
            ZOOMABLE_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) } &&
                (link.size ?: 0L) <= MAX_ZOOM_BYTES
        }?.href

    companion object {
        fun from(links: List<RenditionLinkDto>): Renditions {
            val images = links
                .filter { it.rel != "captions" }
                .mapNotNull { link -> link.href?.toHttpsUrlOrNull()?.let { link.copy(href = it.toString()) } }

            fun named(size: String): RenditionLinkDto? =
                images.firstOrNull { it.href.orEmpty().substringAfterLast('/').contains("~$size.") }

            return Renditions(
                thumb = (named("thumb") ?: images.firstOrNull { it.rel == "preview" })?.href,
                small = named("small")?.href,
                medium = named("medium")?.href,
                large = named("large")?.href,
                original = named("orig") ?: images.firstOrNull { it.rel == "canonical" },
            )
        }
    }
}

/**
 * The library hands out `http://` links with raw spaces in them. Android refuses cleartext and an
 * unencoded space, so both are fixed here (the asset host serves the same files over HTTPS).
 */
private fun String.toHttpsUrlOrNull() =
    trim().toHttpUrlOrNull()?.newBuilder()?.scheme("https")?.build()

private fun HttpUrl.fileName(): String = pathSegments.lastOrNull().orEmpty()

private fun String.toLocalDateOrNull(): LocalDate? = try {
    OffsetDateTime.parse(this).toLocalDate()
} catch (_: DateTimeParseException) {
    try {
        LocalDate.parse(take(ISO_DATE_LENGTH))
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Descriptions are plain text with the odd HTML link or line break left in. */
private fun String.cleanText(): String = this
    .replace(LINE_BREAK, "\n")
    .replace(TAG, "")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")
    .trim()

private val LINE_BREAK = Regex("""<br\s*/?>|</p>""", RegexOption.IGNORE_CASE)
private val TAG = Regex("""<[^>]+>""")

private val ZOOMABLE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
private const val MAX_ZOOM_BYTES = 15L * 1024 * 1024
private const val ISO_DATE_LENGTH = 10

private val VIDEO_RENDITIONS = listOf("~medium.mp4", "~small.mp4", "~mobile.mp4", "~large.mp4", "~orig.mp4")
private val VIDEO_EXTENSIONS = listOf(".mp4", ".m4v", ".mov")
private val AUDIO_RENDITIONS = listOf("~128k.mp3", "~128k.m4a", "~64k.m4a", "~orig.mp3", "~orig.m4a")
private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".wav")
