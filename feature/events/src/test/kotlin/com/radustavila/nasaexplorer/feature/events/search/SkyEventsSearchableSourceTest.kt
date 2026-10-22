package com.radustavila.nasaexplorer.feature.events.search

import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestSkyEvents
import com.radustavila.nasaexplorer.feature.events.SkyEventsFixture
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkyEventsSearchableSourceTest {

    private val fixture = SkyEventsFixture()
    private val source = SkyEventsSearchableSource(
        ApplicationProvider.getApplicationContext(),
        fixture.observeSkyEvents,
        fixture.clock,
    )

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun `finds events by their localised name, soonest first`() = runTest {
        val hits = search("eclipse")

        assertEquals(listOf("Total solar eclipse", "Partial lunar eclipse"), hits.map { it.title })
        val eclipse = hits.first()
        assertEquals(SearchSource.SKY_EVENTS, eclipse.source)
        assertEquals(SearchTarget.SkyEventEntry(TestSkyEvents.totalSolarEclipse().id), eclipse.target)
        assertEquals(LocalDate.parse("2026-08-12"), eclipse.date)
    }

    @Test
    fun `matches category words and data names too`() = runTest {
        assertEquals(listOf("Perseids peak"), search("meteor shower").map { it.title })
        assertEquals(listOf("Perseids peak"), search("swift tuttle").map { it.title })
        assertEquals(listOf("Venus at greatest elongation"), search("venus").map { it.title })
        assertEquals(emptyList<String>(), search("comet").map { it.title })
    }

    private suspend fun search(query: String): List<SearchHit> =
        (source.search(query, limit = 10) as DataResult.Success).data
}
