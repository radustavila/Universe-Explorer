package com.radustavila.nasaexplorer.core.model

import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pages of NASA's own APOD site the app opens itself (the manifest claims them, unverified, since
 * the domain is not ours): `apod.nasa.gov/apod/apYYMMDD.html`, one archive day, and
 * `apod.nasa.gov/apod/astropix.html`, today's picture.
 */
object ApodWebLink {

    /** The site publishes against US Eastern dates, so "today" on it is today in New York. */
    val SITE_ZONE: ZoneId = ZoneId.of("America/New_York")

    /**
     * The archive day [url] shows, with [today] standing in for the site's "today" page; null for any
     * other address, which the app leaves to the browser.
     */
    fun dateOf(url: String, today: LocalDate): LocalDate? {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
        if (uri.host?.lowercase()?.removePrefix("www.") != HOST) return null
        val path = uri.path.orEmpty()
        return when {
            TODAY_PAGE.matches(path) -> today
            ARCHIVE_PAGE.matches(path) -> ApodLink.apodDateOf(url)
            else -> null
        }
    }

    private const val HOST = "apod.nasa.gov"
    private val WEB_SCHEMES = setOf("http", "https")
    private val TODAY_PAGE = Regex("""^/apod/astropix\.html?$""", RegexOption.IGNORE_CASE)
    private val ARCHIVE_PAGE = Regex("""^/apod/ap\d{6}\.html?$""", RegexOption.IGNORE_CASE)
}
