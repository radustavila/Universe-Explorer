package com.radustavila.nasaexplorer.data.news.mapper

import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The news item for one feed entry, or `null` when it is unusable: no web link, no title, or a date
 * that does not parse. NASA's main feeds also carry each day's APOD as an article; those are left
 * out, as the Today tab is built around APOD already.
 */
internal fun FeedEntryDto.toNewsItemOrNull(feed: NewsFeed): NewsItem? {
    val url = (link ?: videoId?.let { "$YOUTUBE_WATCH_URL$it" })
        ?.trim()
        ?.takeIf { (it.startsWith("https://") || it.startsWith("http://")) && APOD_ARTICLE !in it }
    val title = title?.let(Html::toPlainText)?.takeIf(String::isNotEmpty)
    val publishedAt = published?.let(::parseFeedDate)
    if (url == null || title == null || publishedAt == null) return null

    val image = when (feed.kind) {
        NewsKind.IMAGE_OF_THE_DAY -> enclosureImage()
        NewsKind.VIDEO -> thumbnailUrl?.takeIf(::isWebImage)?.let { FeedImage(listUrl = it, fullUrl = it) }
        NewsKind.STORY, NewsKind.SCIENCE -> contentHtml?.let(::articleImage)
    }
    return NewsItem(
        id = url,
        kind = feed.kind,
        title = title,
        url = url,
        publishedAt = publishedAt,
        summary = if (feed.kind == NewsKind.VIDEO) {
            mediaDescription?.let(::videoSummary)
        } else {
            description?.let(::articleSummary)
        },
        imageUrl = image?.listUrl,
        fullImageUrl = image?.fullUrl,
        imageCredit = image?.credit,
        isLive = feed.kind == NewsKind.VIDEO && LIVE.containsMatchIn(title),
    )
}

/** RSS dates are RFC 1123 (`Wed, 21 Oct 2026 15:07 GMT`, seconds optional); Atom's are ISO-8601. */
internal fun parseFeedDate(raw: String): Instant? {
    val text = raw.trim()
    return try {
        ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(text).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

/** A picture for an item: one sized for a list row, the largest worth opening, and its credit. */
internal data class FeedImage(val listUrl: String, val fullUrl: String, val credit: String? = null)

/**
 * The Image of the Day's enclosure is the original upload, which can be hundreds of megabytes (a
 * 208 MB PNG on 2026-10-20). nasa.gov's WordPress resizes uploads on request (`?w=`), so the app
 * asks for a list-sized and a screen-sized copy instead.
 */
private fun FeedEntryDto.enclosureImage(): FeedImage? {
    val url = enclosureUrl?.trim()?.takeIf { isWebImage(it) && enclosureType?.startsWith("image/") != false }
    return when {
        url == null -> null
        url.isResizableUpload() -> FeedImage(listUrl = "$url?w=$LIST_IMAGE_WIDTH", fullUrl = "$url?w=$FULL_IMAGE_WIDTH")
        else -> FeedImage(listUrl = url, fullUrl = url)
    }
}

private fun String.isResizableUpload(): Boolean =
    '?' !in this && RESIZABLE_HOSTS.any { host -> startsWith("https://$host/wp-content/uploads/") }

/**
 * An article's first picture: the smallest `srcset` candidate wide enough for a list row (else its
 * `src`), and its credit, which may name rights holders other than NASA. NASA's pages put the
 * credit in the caption right after the picture (`<figure><img></figure><figcaption>`), so it is
 * the first credit between the picture and the next one.
 */
internal fun articleImage(html: String): FeedImage? {
    val images = IMG_TAG.findAll(html).toList()
    val index = images.indexOfFirst { tag -> Html.attribute(tag.value, "src")?.let(::isWebImage) == true }
    if (index < 0) return null

    val match = images[index]
    val src = Html.attribute(match.value, "src").orEmpty()
    val listUrl = Html.attribute(match.value, "srcset")?.let(::listSizedCandidate) ?: src
    val captionStart = match.range.last + 1
    val nextImage = images.getOrNull(index + 1)?.range?.first ?: html.length
    val captionEnd = minOf(nextImage, captionStart + MAX_CAPTION_DISTANCE)
    val credit = CREDITS.find(html.substring(captionStart, captionEnd.coerceAtMost(html.length)))
        ?.groupValues?.get(1)
        ?.let(Html::toPlainText)
        ?.removePrefix(CREDIT_PREFIX)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return FeedImage(listUrl = listUrl, fullUrl = src, credit = credit)
}

/** `url 300w, url 768w, ...`; URLs may hold commas (`?resize=300,225`), candidates are split on ", ". */
private fun listSizedCandidate(srcset: String): String? {
    val candidates = srcset.split(SRCSET_SEPARATOR).mapNotNull { candidate ->
        val parts = candidate.trim().split(WHITESPACE)
        val width = parts.getOrNull(1)?.removeSuffix("w")?.toIntOrNull() ?: return@mapNotNull null
        parts[0].takeIf(::isWebImage)?.let { it to width }
    }
    val wideEnough = candidates.filter { it.second >= LIST_IMAGE_WIDTH }.minByOrNull { it.second }
    return (wideEnough ?: candidates.maxByOrNull { it.second })?.first
}

private fun isWebImage(url: String): Boolean =
    url.startsWith("https://") && !url.substringBefore('?').endsWith(".svg", ignoreCase = true)

/**
 * The description without WordPress's "The post ... appeared first on ..." footer, as plain text,
 * cut to a few sentences.
 */
internal fun articleSummary(html: String): String? =
    Html.toPlainText(html.replace(POST_FOOTER, " "))
        .replace(ELLIPSIS_MARK, "…")
        .shortened()

/** A video description's first paragraph; the rest is links and channel boilerplate. */
private fun videoSummary(text: String): String? =
    Html.toPlainText(text.trim().substringBefore("\n\n")).shortened()

private fun String.shortened(): String? = when {
    isEmpty() -> null
    length <= MAX_SUMMARY_LENGTH -> this
    else -> take(MAX_SUMMARY_LENGTH).substringBeforeLast(' ').trimEnd(',', ';', ':', ' ') + "…"
}

internal const val LIST_IMAGE_WIDTH = 800
internal const val FULL_IMAGE_WIDTH = 2048
private const val MAX_SUMMARY_LENGTH = 280
private const val MAX_CAPTION_DISTANCE = 4_000
private const val CREDIT_PREFIX = "Credit:"
private const val APOD_ARTICLE = "/image-article/apod-"
private const val YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v="

private val RESIZABLE_HOSTS = listOf("www.nasa.gov", "science.nasa.gov")

/**
 * YouTube's feed does not say which entries are live streams; NASA titles them so ("Live
 * High-Definition Views from the International Space Station"), and recordings of past events drop
 * the word.
 */
private val LIVE = Regex("""\blive\b""", RegexOption.IGNORE_CASE)

private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
private val CREDITS = Regex(
    """<div class="hds-credits"[^>]*>(.*?)</div>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val POST_FOOTER = Regex(
    """<p>\s*The post .*?appeared first on.*?</p>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)
private val ELLIPSIS_MARK = Regex("""\s*\[(…|\.\.\.)]""")
private val SRCSET_SEPARATOR = Regex(""",\s+""")
private val WHITESPACE = Regex("""\s+""")
