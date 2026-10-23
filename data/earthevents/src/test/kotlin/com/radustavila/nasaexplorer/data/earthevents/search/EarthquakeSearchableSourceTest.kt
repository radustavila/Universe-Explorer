package com.radustavila.nasaexplorer.data.earthevents.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes.NOW
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EarthquakeSearchableSourceTest {

    private val repository = FakeEarthquakeRepository(
        listOf(
            TestEarthquakes.earthquake(id = "sicily"),
            TestEarthquakes.earthquake(id = "calabria", magnitude = 6.2, place = "10 km S of Crotone, Italy"),
            TestEarthquakes.earthquake(
                id = "old",
                place = "5 km N of Rome, Italy",
                time = NOW.minus(Duration.ofDays(9)),
            ),
            TestEarthquakes.small(),
        ),
    )
    private val source = EarthquakeSearchableSource(repository, FakeTimeProvider(currentInstant = NOW))

    @Test
    fun `quakes match by place, strongest first, within the map's windows`() = runTest {
        val hits = (source.search("italy", limit = 10) as DataResult.Success).data

        assertEquals(listOf("calabria", "sicily"), hits.map { it.id })
        val top = hits.first()
        assertEquals(SearchSource.EARTHQUAKES, top.source)
        assertEquals("Crotone, Italy", top.title)
        assertEquals("M 6.2", top.subtitle)
        assertEquals(SearchTarget.EarthquakeEntry("calabria"), top.target)
        assertEquals(LocalDate.of(2026, 10, 23), top.date)
    }

    @Test
    fun `the limit applies`() = runTest {
        assertEquals(1, (source.search("italy", limit = 1) as DataResult.Success).data.size)
    }
}
