package com.radustavila.nasaexplorer.data.news.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakePodcastRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastSearchableSourceTest {

    private val source = PodcastSearchableSource(
        FakePodcastRepository(
            listOf(TestPodcasts.houston(), TestPodcasts.curiousUniverse(), TestPodcasts.smallSteps()),
        ),
    )

    @Test
    fun `episodes match by title and show notes and open on their own screen`() = runTest {
        val hits = (source.search("artemis", limit = 10) as DataResult.Success).data

        val hit = hits.single()
        assertEquals(SearchSource.PODCASTS, hit.source)
        assertEquals(TestPodcasts.houston().id, hit.id)
        assertEquals(SearchTarget.PodcastEpisodeEntry(TestPodcasts.houston().id), hit.target)
        assertEquals(LocalDate.of(2026, 10, 16), hit.date)

        val mars = (source.search("mars simulation", limit = 10) as DataResult.Success).data
        assertEquals(listOf(TestPodcasts.curiousUniverse().id), mars.map { it.id })
    }
}
