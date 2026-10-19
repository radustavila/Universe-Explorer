package com.radustavila.nasaexplorer.data.nasa.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of `science.nasa.gov/wp-json/wp/v2/apod-basic`, where APOD lives since it moved off
 * apod.nasa.gov (api.nasa.gov still scrapes the old site and now answers every date with a NASA logo
 * titled "NASA Science").
 *
 * Unlike the old API, text fields are HTML and the media itself is only referenced from
 * [basicHtml], the classic APOD page. [toApodDto] turns it into the shape the rest of the module
 * already understands.
 */
@Serializable
data class ApodBasicDto(
    @SerialName("date") val date: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("explanation") val explanation: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("hdurl") val hdUrl: String? = null,
    @SerialName("copyright") val copyright: String? = null,
    @SerialName("basic_html") val basicHtml: String? = null,
    /** The entry's own page on science.nasa.gov; apod.nasa.gov no longer resolves recent ones. */
    @SerialName("permalink") val permalink: String? = null,
    /** Screen reader text for the picture. Old entries carry a generic placeholder instead. */
    @SerialName("alt") val alt: String? = null,
)

fun ApodBasicDto.toApodDto(): ApodDto {
    val mediaUrl = basicHtml?.let(::firstEmbeddedMediaUrl)
    val stillImage = hdUrl?.trim()?.takeUnless { it.isBlank() || PLACEHOLDER_IMAGE_MARKER in it }
    val isVideo = mediaType == "video"

    val linkedExplanation = explanation?.let(::cleanExplanation)

    return ApodDto(
        date = date,
        title = title?.htmlToPlainText(),
        explanation = linkedExplanation?.text,
        mediaType = mediaType,
        url = mediaUrl,
        hdUrl = stillImage.takeUnless { isVideo },
        // The still frame NASA picked for a video; when there is none the model derives one.
        thumbnailUrl = stillImage.takeIf { isVideo },
        copyright = copyright?.htmlToPlainText()?.replace(CREDIT_LABEL, "")?.takeIf(String::isNotBlank),
        permalink = permalink?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
        alt = alt?.htmlToPlainText()?.takeUnless { it.isBlank() || GENERIC_ALT.containsMatchIn(it) },
        explanationLinks = linkedExplanation?.links.orEmpty(),
    )
}

/** The picture, video file or player embed the page features: the first element with a `src`. */
private fun firstEmbeddedMediaUrl(html: String): String? =
    EMBEDDED_MEDIA.find(html)?.groupValues?.get(1)?.trim()
        // Embeds are often protocol-relative (`//www.youtube.com/embed/...`).
        ?.let { if (it.startsWith("//")) "https:$it" else it }

/**
 * Drops the "Explanation:" label and the site notices NASA appends after a blank line ("Tomorrow's
 * picture", "Your Sky Surprise", announcements), which are not part of the entry. The links in the
 * text are kept, as ranges of the plain text that results.
 */
private fun cleanExplanation(html: String): LinkedText {
    val linked = htmlToLinkedText(html.split(TRAILING_NOTICES, limit = 2).first())
    val label = EXPLANATION_LABEL.find(linked.text) ?: return linked
    val cut = label.range.last + 1
    return LinkedText(
        text = linked.text.substring(cut),
        links = linked.links
            .filter { it.start >= cut }
            .map { it.copy(start = it.start - cut, end = it.end - cut) },
    )
}

private fun String.htmlToPlainText(): String = htmlToLinkedText(this).text

/** Plain text plus the links it carried, addressed as character ranges of that text. */
private class LinkedText(val text: String, val links: List<ExplanationLinkDto>)

/**
 * Turns an HTML fragment into readable plain text: tags go, entities are decoded, whitespace is
 * collapsed, and the spacing that tag removal leaves around punctuation ("NASA , JPL", "( Sydney )")
 * is tightened back up.
 *
 * Every `<a href>` is first wrapped in private marker characters, which survive tag removal and
 * entity decoding untouched; the final pass drops them and records where each link's text ended up,
 * so the ranges stay exact however much the text around them was rewritten.
 */
private fun htmlToLinkedText(html: String): LinkedText {
    val urls = mutableListOf<String?>()
    val marked = ANCHOR.replace(html) { match ->
        urls += HREF.find(match.groupValues[1])?.groupValues?.get(1)?.let(::absoluteLinkUrl)
        "$LINK_START${match.groupValues[2]}$LINK_END"
    }
        .replace(TAG, " ")
        .decodeHtmlEntities()

    val text = StringBuilder()
    val links = mutableListOf<ExplanationLinkDto>()
    var hasPendingSpace = false
    var isLinkOpen = false
    var linkStart = -1
    var linkIndex = 0

    for (char in marked) {
        when {
            char == LINK_START -> {
                isLinkOpen = true
                linkStart = -1
            }

            char == LINK_END -> {
                val url = urls.getOrNull(linkIndex++)
                if (isLinkOpen && linkStart >= 0 && url != null) {
                    links += ExplanationLinkDto(start = linkStart, end = text.length, url = url)
                }
                isLinkOpen = false
            }

            char in HTML_WHITESPACE -> hasPendingSpace = text.isNotEmpty()

            else -> {
                if (hasPendingSpace && char !in CLOSING_PUNCTUATION && text.last() != '(') {
                    text.append(' ')
                }
                hasPendingSpace = false
                // A link's text starts at its first visible character, never at the space before it.
                if (isLinkOpen && linkStart < 0) linkStart = text.length
                text.append(char)
            }
        }
    }
    return LinkedText(text = text.toString(), links = links)
}

/** Links are absolute in current entries; anything else is resolved against the classic archive. */
private fun absoluteLinkUrl(href: String): String? {
    val trimmed = href.trim().decodeHtmlEntities()
    val absolute = when {
        trimmed.isEmpty() || trimmed.startsWith("#") -> return null
        trimmed.startsWith("//") -> "https:$trimmed"
        SCHEME.containsMatchIn(trimmed) -> trimmed
        else -> CLASSIC_ARCHIVE_BASE + trimmed.removePrefix("/apod/").removePrefix("/")
    }
    return absolute.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
}

private fun String.decodeHtmlEntities(): String = HTML_ENTITY.replace(this) { match ->
    val entity = match.groupValues[1]
    when {
        entity.startsWith("#x", ignoreCase = true) ->
            entity.drop(2).toIntOrNull(radix = 16)?.let(::codePointToString)
        entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.let(::codePointToString)
        else -> NAMED_ENTITIES[entity]
    } ?: match.value
}

private fun codePointToString(codePoint: Int): String? =
    codePoint.takeIf(Character::isValidCodePoint)?.let { String(Character.toChars(it)) }

/** NASA's generic "news" image, sent as `hdurl` for entries that have no still image of their own. */
private const val PLACEHOLDER_IMAGE_MARKER = "/images/misc/news-thumbnail"

private val EMBEDDED_MEDIA = Regex(
    """<(?:img|video|source|iframe|embed)\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""",
    RegexOption.IGNORE_CASE,
)
private val TRAILING_NOTICES = Regex("""(?:<br\s*/?>\s*){2,}\s*<(?:strong|b)\b""", RegexOption.IGNORE_CASE)
private val EXPLANATION_LABEL = Regex("""^Explanation:\s*""", RegexOption.IGNORE_CASE)

/** "Credit:", "Image Credit & Copyright:", "Video Credit:" and the like. */
private val CREDIT_LABEL = Regex("""^[^:]{0,40}\b(?:Credit|Copyright)\b[^:]*:\s*""", RegexOption.IGNORE_CASE)
private val TAG = Regex("<[^>]*>")
private val ANCHOR = Regex("""<a\b([^>]*)>(.*?)</a\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HREF = Regex("""\bhref\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")
private const val CLASSIC_ARCHIVE_BASE = "https://apod.nasa.gov/apod/"

/** Private-use characters that cannot occur in NASA's text, marking where a link starts and ends. */
private const val LINK_START = '\uE000'
private const val LINK_END = '\uE001'

/** What a regex `\s` matches; the same set the plain-text conversion always collapsed. */
private const val HTML_WHITESPACE = " \t\n\u000B\u000C\r"
private const val CLOSING_PUNCTUATION = ",.;:!?)"

/**
 * What old entries carry instead of a description, inherited from the classic archive pages; it
 * describes nothing, so the title is announced instead.
 */
private val GENERIC_ALT = Regex("""^See Explanation\b""", RegexOption.IGNORE_CASE)
private val HTML_ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
)
