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
)

fun ApodBasicDto.toApodDto(): ApodDto {
    val mediaUrl = basicHtml?.let(::firstEmbeddedMediaUrl)
    val stillImage = hdUrl?.trim()?.takeUnless { it.isBlank() || PLACEHOLDER_IMAGE_MARKER in it }
    val isVideo = mediaType == "video"

    return ApodDto(
        date = date,
        title = title?.htmlToPlainText(),
        explanation = explanation?.let(::cleanExplanation),
        mediaType = mediaType,
        url = mediaUrl,
        hdUrl = stillImage.takeUnless { isVideo },
        // The still frame NASA picked for a video; when there is none the model derives one.
        thumbnailUrl = stillImage.takeIf { isVideo },
        copyright = copyright?.htmlToPlainText()?.replace(CREDIT_LABEL, "")?.takeIf(String::isNotBlank),
    )
}

/** The picture, video file or player embed the page features: the first element with a `src`. */
private fun firstEmbeddedMediaUrl(html: String): String? =
    EMBEDDED_MEDIA.find(html)?.groupValues?.get(1)?.trim()
        // Embeds are often protocol-relative (`//www.youtube.com/embed/...`).
        ?.let { if (it.startsWith("//")) "https:$it" else it }

/**
 * Drops the "Explanation:" label and the site notices NASA appends after a blank line ("Tomorrow's
 * picture", "Your Sky Surprise", announcements), which are not part of the entry.
 */
private fun cleanExplanation(html: String): String =
    html.split(TRAILING_NOTICES, limit = 2).first()
        .htmlToPlainText()
        .replace(EXPLANATION_LABEL, "")

private fun String.htmlToPlainText(): String = this
    .replace(TAG, " ")
    .decodeHtmlEntities()
    .replace(WHITESPACE_RUN, " ")
    // Tag removal leaves "NASA , JPL" and "( Sydney )"; tighten punctuation back up.
    .replace(SPACE_BEFORE_PUNCTUATION, "$1")
    .replace(SPACE_AFTER_OPENING_PARENTHESIS, "(")
    .trim()

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
private val HTML_ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
)
private val WHITESPACE_RUN = Regex("\\s+")
private val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?)])""")
private val SPACE_AFTER_OPENING_PARENTHESIS = Regex("""\(\s+""")
