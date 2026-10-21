package com.radustavila.nasaexplorer.core.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `terms are lowercase words without punctuation`() {
        assertEquals(listOf("kepler", "22", "b"), SearchText.terms("  Kepler-22 b "))
        assertEquals(listOf("café"), SearchText.terms("Café, café"))
        assertTrue(SearchText.terms(" ?! ").isEmpty())
    }

    @Test
    fun `every term must occur in one of the texts`() {
        val terms = SearchText.terms("crab nebula")
        assertTrue(SearchText.matchesAll(terms, "The Crab", "a supernova remnant nebula"))
        assertFalse(SearchText.matchesAll(terms, "The Crab", null))
    }

    @Test
    fun `match ranges cover every occurrence, merged where they touch`() {
        assertEquals(
            listOf(0..5, 13..18),
            SearchText.matchRanges("Saturn's and Saturn", SearchText.terms("saturn")),
        )
        assertEquals(listOf(0..5), SearchText.matchRanges("Andromeda", listOf("andr", "drom")))
    }

    @Test
    fun `a snippet keeps the first match in view and marks the cuts`() {
        val text = "word ".repeat(60) + "Jupiter's Great Red Spot " + "tail ".repeat(60)
        val snippet = requireNotNull(SearchText.snippet(text, listOf("jupiter"), maxLength = 60))

        assertTrue(snippet, snippet.contains("Jupiter"))
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"))
        assertTrue(snippet.length <= 62)
        assertEquals("Short text", SearchText.snippet("  Short\n text ", listOf("x")))
        assertNull(SearchText.snippet("  ", listOf("x")))
    }

    @Test
    fun `scores follow how much of the title the query explains`() {
        val scores = listOf("Mars", "Mars Rover", "Rover on Mars", "Marsquake", "Moon and Mars", "Phobos")
            .map { SearchText.score(it, "mars rover") }

        assertEquals(
            listOf(
                SearchText.SCORE_PARTIAL,
                SearchText.SCORE_EXACT,
                SearchText.SCORE_WORD_PREFIX,
                SearchText.SCORE_PARTIAL,
                SearchText.SCORE_PARTIAL,
                SearchText.SCORE_ELSEWHERE,
            ),
            scores,
        )
        assertEquals(SearchText.SCORE_PREFIX, SearchText.score("Mars Rover Selfie", "mars rover"))
        assertEquals(SearchText.SCORE_CONTAINS, SearchText.score("Supermarsrovers", "mars rover"))
    }
}
