package com.radustavila.nasaexplorer.core.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledCityDirectoryTest {

    private val directory = BundledCityDirectory()

    @Test
    fun `search ignores case and diacritics`() = runTest {
        assertEquals("Iași", directory.search("iasi", limit = 8).first().name)
        assertEquals("São Paulo", directory.search("SAO PAU", limit = 8).first().name)
    }

    @Test
    fun `names starting with the query come before names containing it`() = runTest {
        val names = directory.search("bu", limit = 8).map { it.name }

        assertEquals(listOf("Bucharest", "Budapest", "Buenos Aires"), names.take(3))
    }

    @Test
    fun `a country finds its cities`() = runTest {
        val cities = directory.search("Romania", limit = 20)

        assertTrue(cities.size >= 5)
        assertTrue(cities.all { it.country == "Romania" })
    }

    @Test
    fun `a blank query finds nothing and the limit is respected`() = runTest {
        assertTrue(directory.search("  ", limit = 8).isEmpty())
        assertEquals(3, directory.search("a", limit = 3).size)
    }

    @Test
    fun `every bundled city has valid coordinates and a unique name per country`() {
        assertEquals(BUNDLED_CITIES.size, BUNDLED_CITIES.map { it.name to it.country }.toSet().size)
    }
}
