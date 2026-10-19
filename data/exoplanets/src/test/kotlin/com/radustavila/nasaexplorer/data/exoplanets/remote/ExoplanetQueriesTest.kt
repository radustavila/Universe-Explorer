package com.radustavila.nasaexplorer.data.exoplanets.remote

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoplanetQueriesTest {

    @Test
    fun `catalog query reads pscomppars filtered by year, distance and radius`() {
        assertEquals(
            "select pl_name,hostname,disc_year,disc_pubdate,disc_method,disc_facility," +
                "pl_rade,pl_bmasse,pl_orbper,pl_eqt,sy_dist from pscomppars" +
                " where disc_year>=2025 or sy_dist<15 or (pl_rade between 0.8 and 1.25)",
            ExoplanetQueries.catalog(newestSinceYear = 2025),
        )
    }

    @Test
    fun `no query combines top with order by, and none reads the per-publication table`() {
        val queries = listOf(ExoplanetQueries.catalog(2025), ExoplanetQueries.CONFIRMED_COUNT)

        queries.forEach { query ->
            assertFalse(query, Regex("\\btop\\b", RegexOption.IGNORE_CASE).containsMatchIn(query))
            assertFalse(query, Regex("\\bfrom ps\\b", RegexOption.IGNORE_CASE).containsMatchIn(query))
            assertTrue(query, query.contains("from pscomppars"))
        }
    }

    @Test
    fun `count query`() {
        assertEquals("select count(*) from pscomppars", ExoplanetQueries.CONFIRMED_COUNT)
    }

    @Test
    fun `encoding percent-encodes operators and spaces, never with a plus`() {
        assertEquals(
            "select%20count%28*%29%20from%20pscomppars%20where%20disc_year%3E%3D2026%20or%20sy_dist%3C15",
            ExoplanetQueries.encode("select count(*) from pscomppars where disc_year>=2026 or sy_dist<15"),
        )
        assertEquals("pl_rade%2B1", ExoplanetQueries.encode("pl_rade+1"))
        assertEquals("a%26b%3Dc%23d", ExoplanetQueries.encode("a&b=c#d"))
    }

    @Test
    fun `encoded catalog query decodes back to the original`() {
        val adql = ExoplanetQueries.catalog(2025)

        val encoded = ExoplanetQueries.encode(adql)

        assertFalse(encoded.contains(' '))
        assertFalse(encoded.contains('+'))
        assertEquals(adql, URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()))
    }
}
