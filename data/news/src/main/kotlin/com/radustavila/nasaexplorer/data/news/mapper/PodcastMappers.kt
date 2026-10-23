package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import java.time.Duration

/**
 * The episode for one feed entry, or `null` when it is unusable: no audio enclosure, no title, or
 * a date that does not parse. [showImageUrl] is the feed's own artwork, for episodes without one.
 *
 * The enclosure URL is kept exactly as the feed gives it (Curious Universe's go through a
 * `chrt.fm` tracking redirect): episodes are streamed from NASA's host, never re-hosted.
 */
internal fun FeedEntryDto.toPodcastEpisodeOrNull(show: PodcastShow, showImageUrl: String?): PodcastEpisode? {
    val audioUrl = enclosureUrl?.trim()?.takeIf { it.isWebUrl() && enclosureType.isAudioOrUnknown() }
    val title = title?.let(Html::toPlainText)?.takeIf(String::isNotEmpty)
    val publishedAt = published?.let(::parseFeedDate)
    if (audioUrl == null || title == null || publishedAt == null) return null

    return PodcastEpisode(
        id = guid?.trim()?.takeIf(String::isNotEmpty) ?: audioUrl,
        show = show,
        title = title,
        publishedAt = publishedAt,
        audioUrl = audioUrl,
        duration = duration?.let(::parseItunesDuration),
        summary = (contentHtml ?: description)?.let(::episodeSummary),
        imageUrl = (itunesImageUrl ?: showImageUrl)?.trim()?.takeIf { it.startsWith("https://") }?.let(::artworkUrl),
        pageUrl = link?.trim()?.takeIf(String::isWebUrl),
    )
}

/** `itunes:duration` is whole seconds (`3617`) on NASA's feeds; the spec also allows `[h:]mm:ss`. */
internal fun parseItunesDuration(raw: String): Duration? {
    val parts = raw.trim().split(':')
    val numbers = parts.mapNotNull { part -> part.toLongOrNull()?.takeIf { it >= 0 } }
    if (numbers.size != parts.size || numbers.size > MAX_DURATION_PARTS) return null
    val seconds = numbers.fold(0L) { total, part -> total * SECONDS_PER_MINUTE + part }
    return Duration.ofSeconds(seconds).takeIf { seconds > 0 }
}

/**
 * Megaphone's artwork is up to 3000 px square; its image service (imgix) resizes on request, so
 * the app asks for a size that fits both a list row and the player. Other hosts are left as they
 * are.
 */
internal fun artworkUrl(url: String): String {
    if (!url.startsWith(MEGAPHONE_IMAGES)) return url
    val base = url.substringBefore('?')
    val query = url.substringAfter('?', missingDelimiterValue = "")
        .split('&')
        .filter { it.isNotEmpty() && it.substringBefore('=') !in SIZE_PARAMETERS }
    return (query + listOf("w=$ARTWORK_SIZE", "h=$ARTWORK_SIZE")).joinToString(separator = "&", prefix = "$base?")
}

/** Show notes as plain text, paragraphs kept as blank lines, cut at a generous length. */
private fun episodeSummary(html: String): String? {
    val paragraphs = html.split(PARAGRAPH_BREAK)
        .map(Html::toPlainText)
        .filter(String::isNotEmpty)
    val text = paragraphs.joinToString("\n\n")
    return when {
        text.isEmpty() -> null
        text.length <= MAX_SUMMARY_LENGTH -> text
        else -> text.take(MAX_SUMMARY_LENGTH).substringBeforeLast(' ').trimEnd(',', ';', ':', ' ') + "…"
    }
}

private fun String?.isAudioOrUnknown(): Boolean = this == null || startsWith("audio/")

private fun String.isWebUrl(): Boolean = startsWith("https://") || startsWith("http://")

internal const val ARTWORK_SIZE = 600
private const val MEGAPHONE_IMAGES = "https://megaphone.imgix.net/"
private const val MAX_DURATION_PARTS = 3
private const val SECONDS_PER_MINUTE = 60L
private const val MAX_SUMMARY_LENGTH = 4_000
private val SIZE_PARAMETERS = setOf("w", "h", "max-w", "max-h")
private val PARAGRAPH_BREAK = Regex("""</p>|<br\s*/?>\s*<br\s*/?>|\n\s*\n""", RegexOption.IGNORE_CASE)
