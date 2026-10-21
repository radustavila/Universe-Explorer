package com.radustavila.nasaexplorer.core.domain.search

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.testing.data.TestSearchHits
import com.radustavila.nasaexplorer.core.testing.fake.FakeSearchableSource
import java.time.LocalDate
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchUseCaseTest {

    private val apod = FakeSearchableSource(SearchSource.APOD)
    private val exoplanets = FakeSearchableSource(SearchSource.EXOPLANETS)
    private val library = FakeSearchableSource(SearchSource.NASA_LIBRARY)
    private val launches = FakeSearchableSource(SearchSource.LAUNCHES)

    private val search = UniversalSearchUseCase(setOf(launches, exoplanets, library, apod))

    @Test
    fun `asks every source at once rather than one after another`() = runTest {
        listOf(apod, exoplanets, library, launches).forEach { it.delayMillis = 1_000 }

        search("kepler")

        // Four sources of one second each: one second in all only if they ran side by side.
        assertEquals(1_000, currentTime)
        assertTrue(listOf(apod, exoplanets, library, launches).all { it.queries == listOf("kepler") })
    }

    @Test
    fun `a failing, throwing or hanging source does not keep the others from answering`() = runTest {
        apod.hits = listOf(TestSearchHits.apod("Kepler's Supernova"))
        exoplanets.result = DataResult.Failure(AppError.Database("disk I/O"))
        library.error = IllegalStateException("closed")
        launches.delayMillis = UniversalSearchUseCase.SOURCE_TIMEOUT_MILLIS + 1

        val results = search("kepler")

        assertEquals(listOf(SearchSource.APOD), results.groups.map { it.source })
        assertEquals(setOf(SearchSource.EXOPLANETS, SearchSource.NASA_LIBRARY, SearchSource.LAUNCHES), results.failedSources)
    }

    @Test
    fun `ranks title matches above matches elsewhere, keeping each source's order between equals`() = runTest {
        apod.hits = listOf(
            TestSearchHits.apod("Galaxies Near Andromeda", LocalDate.of(2024, 3, 3), snippet = "about Kepler"),
            TestSearchHits.apod("The Kepler Field", LocalDate.of(2024, 3, 2)),
            TestSearchHits.apod("Kepler's Supernova", LocalDate.of(2024, 3, 1)),
            TestSearchHits.apod("Kepler", LocalDate.of(2024, 2, 1)),
        )

        val titles = search("kepler").groups.single().hits.map { it.title }

        assertEquals(listOf("Kepler", "Kepler's Supernova", "The Kepler Field", "Galaxies Near Andromeda"), titles)
    }

    @Test
    fun `orders groups by their best match, then by source`() = runTest {
        apod.hits = listOf(TestSearchHits.apod("A Planet Like Kepler-22 b", snippet = null))
        exoplanets.hits = listOf(TestSearchHits.exoplanet("Kepler-22 b"))
        launches.hits = listOf(TestSearchHits.launch("l1", "Kepler-22 b Survey"))
        library.hits = listOf(TestSearchHits.nasaMedia("PIA1", "Kepler-22 b"))

        val order = search("kepler-22 b").groups.map { it.source }

        // Exact titles first (library before exoplanets by source order), then a prefix, then a word.
        assertEquals(
            listOf(SearchSource.NASA_LIBRARY, SearchSource.EXOPLANETS, SearchSource.LAUNCHES, SearchSource.APOD),
            order,
        )
    }

    @Test
    fun `sources without matches leave no empty group`() = runTest {
        exoplanets.hits = listOf(TestSearchHits.exoplanet("TRAPPIST-1 e"))

        val results = search("trappist")

        assertEquals(listOf(SearchSource.EXOPLANETS), results.groups.map { it.source })
        assertTrue(results.failedSources.isEmpty())
    }

    @Test
    fun `a query without a searchable word asks no source`() = runTest {
        val results = search("  -?! ")

        assertTrue(results.isEmpty)
        assertTrue(apod.queries.isEmpty())
    }

    @Test
    fun `caps each group and drops a source's duplicates`() = runTest {
        val hit = TestSearchHits.exoplanet("Kepler-1 b")
        exoplanets.hits = List(5) { hit } + (2..40).map { TestSearchHits.exoplanet("Kepler-$it b") }

        val hits = search("kepler", limitPerSource = 10).groups.single().hits

        assertEquals(6, hits.size) // ten asked for, five of them the same planet
        assertEquals(hits.map { it.id }.distinct(), hits.map { it.id })
    }
}
