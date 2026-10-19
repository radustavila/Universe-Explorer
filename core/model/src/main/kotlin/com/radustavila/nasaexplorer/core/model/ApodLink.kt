package com.radustavila.nasaexplorer.core.model

import java.net.URI
import java.net.URISyntaxException
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * A link NASA placed in an entry's explanation, addressed as the character range
 * `[start, end)` of [Apod.explanation].
 */
data class ApodLink(
    val start: Int,
    val end: Int,
    val url: String,
) {
    /**
     * The archive day this link points to when it is another APOD entry, so the app can open it
     * itself instead of sending the user to a browser; `null` for any other page.
     */
    val apodDate: LocalDate? get() = apodDateOf(url)

    companion object {

        /**
         * Recognises the three addresses an explanation uses for another entry:
         * `apod.nasa.gov/apod/apYYMMDD.html` (the classic archive), `science.nasa.gov/apod/?date=YYMMDD`
         * and `science.nasa.gov/image-article/apod-YYYY-month-D-slug/` (the current site).
         */
        fun apodDateOf(url: String): LocalDate? {
            val uri = try {
                URI(url.trim())
            } catch (_: URISyntaxException) {
                return null
            }
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
            val path = uri.path.orEmpty()

            val date = when (host) {
                CLASSIC_HOST -> CLASSIC_PAGE.find(path)?.groupValues?.get(1)?.let(::parseShortDate)
                CURRENT_HOST -> ARTICLE_PAGE.find(path)?.let(::parseArticleDate)
                    ?: uri.query?.takeIf { path.trimEnd('/') == "/apod" }
                        ?.split('&')
                        ?.firstOrNull { it.startsWith("date=") }
                        ?.removePrefix("date=")
                        ?.let(::parseShortDate)

                else -> null
            }
            return date?.takeUnless { it.isBefore(Apod.FIRST_PUBLICATION_DATE) }
        }

        /** `yyMMdd`; the archive starts in 1995, so 95-99 are the 1900s and the rest the 2000s. */
        private fun parseShortDate(raw: String): LocalDate? {
            if (!SHORT_DATE.matches(raw)) return null
            val twoDigitYear = raw.substring(0, 2).toInt()
            val year = if (twoDigitYear >= FIRST_ARCHIVE_TWO_DIGIT_YEAR) 1900 + twoDigitYear else 2000 + twoDigitYear
            return dateOrNull(year, raw.substring(2, 4).toInt(), raw.substring(4, 6).toInt())
        }

        private fun parseArticleDate(match: MatchResult): LocalDate? {
            val (year, monthName, day) = match.destructured
            val month = MONTHS[monthName.lowercase()] ?: return null
            return dateOrNull(year.toInt(), month.value, day.toInt())
        }

        private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            null
        }

        private const val CLASSIC_HOST = "apod.nasa.gov"
        private const val CURRENT_HOST = "science.nasa.gov"
        private const val FIRST_ARCHIVE_TWO_DIGIT_YEAR = 95

        private val SHORT_DATE = Regex("""\d{6}""")
        private val CLASSIC_PAGE = Regex("""^/apod/ap(\d{6})\.html?$""", RegexOption.IGNORE_CASE)
        private val ARTICLE_PAGE = Regex("""^/image-article/apod-(\d{4})-([a-z]+)-(\d{1,2})(?:-|/|$)""", RegexOption.IGNORE_CASE)
        private val MONTHS: Map<String, Month> = Month.entries.associateBy {
            it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        }
    }
}
