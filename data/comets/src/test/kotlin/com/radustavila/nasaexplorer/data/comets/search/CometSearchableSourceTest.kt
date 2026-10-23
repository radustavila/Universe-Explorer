package com.radustavila.nasaexplorer.data.comets.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CometSearchableSourceTest {

    private val source = CometSearchableSource(
        FakeCometRepository(
            listOf(
                TestComets.comet(),
                TestComets.comet(designation = "2026 O1", name = "C/2026 O1 (PANSTARRS)"),
            ),
        ),
    )

    @Test
    fun `matches a comet by its discoverer or designation and opens its details`() = runTest {
        val byName = (source.search("panstarrs", limit = 10) as DataResult.Success).data
        val byDesignation = (source.search("2026 a2", limit = 10) as DataResult.Success).data

        val hit = byName.single()
        assertEquals(SearchSource.COMETS, hit.source)
        assertEquals("C/2026 O1 (PANSTARRS)", hit.title)
        assertEquals(SearchTarget.CometEntry("2026 O1"), hit.target)
        assertEquals(LocalDate.of(2026, 12, 22), hit.date)
        assertEquals(listOf("2026 A2"), byDesignation.map { it.id })
    }
}
