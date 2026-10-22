package com.radustavila.nasaexplorer.data.news.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewsRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsSearchableSourceTest {

    private val source = NewsSearchableSource(FakeNewsRepository(TestNews.ALL))

    @Test
    fun `news is found by its title or summary, newest first`() = runTest {
        val hits = (source.search("station", limit = 10) as DataResult.Success).data

        assertEquals(listOf(TestNews.story().id, TestNews.video().id, TestNews.imageOfTheDay().id), hits.map { it.id })
        val story = hits.first()
        assertEquals(SearchSource.NEWS, story.source)
        assertEquals(SearchTarget.NewsEntry(TestNews.story().id), story.target)
        assertEquals(LocalDate.of(2026, 10, 22), story.date)
        assertEquals(TestNews.story().imageUrl, story.imageUrl)
    }

    @Test
    fun `every term must match`() = runTest {
        val hits = (source.search("hubble lenses", limit = 10) as DataResult.Success).data

        assertEquals(listOf(TestNews.science().id), hits.map { it.id })
    }
}
