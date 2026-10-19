package com.radustavila.nasaexplorer.data.exoplanets.remote

import com.radustavila.nasaexplorer.core.model.ExoplanetUnits
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The ADQL the app sends to the Exoplanet Archive's TAP service, built and URL-encoded in this one
 * place.
 *
 * - Queries read `pscomppars` (one row per planet with the archive's preferred values), never `ps`
 *   (one row per publication, several per planet).
 * - No query uses `top N` with `order by`: the archive then returns unsorted rows. The catalogue is
 *   bounded by filters instead and sorted locally by Room.
 */
object ExoplanetQueries {

    /** Columns of [catalog]; `ExoplanetDto` reads them by name. */
    val CATALOG_COLUMNS: List<String> = listOf(
        "pl_name",
        "hostname",
        "disc_year",
        "disc_pubdate",
        "disc_method",
        "disc_facility",
        "pl_rade",
        "pl_bmasse",
        "pl_orbper",
        "pl_eqt",
        "sy_dist",
    )

    /**
     * Systems closer than this (in parsecs, `sy_dist`'s unit) are fetched for the "nearest" list:
     * about 220 planets, comfortably more than the list shows.
     */
    const val NEAREST_WITHIN_PARSECS: Int = 15

    /** Every confirmed planet, for the total shown above the lists. */
    const val CONFIRMED_COUNT: String = "select count(*) from pscomppars"

    /**
     * One request for every list the screen offers: planets discovered since [newestSinceYear], the
     * nearest systems, and Earth-sized planets (about 1,250 rows in 2026).
     */
    fun catalog(newestSinceYear: Int): String {
        val earthSized = ExoplanetUnits.EARTH_SIZED_RADII
        return "select ${CATALOG_COLUMNS.joinToString(",")} from pscomppars" +
            " where disc_year>=$newestSinceYear" +
            " or sy_dist<$NEAREST_WITHIN_PARSECS" +
            " or (pl_rade between ${earthSized.start} and ${earthSized.endInclusive})"
    }

    /**
     * Percent-encodes [adql] for the `query` parameter. Spaces become `%20` rather than the form
     * encoding's `+`, which a TAP service is not required to decode as a space.
     */
    fun encode(adql: String): String =
        URLEncoder.encode(adql, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
