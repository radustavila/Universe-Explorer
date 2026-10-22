package com.radustavila.nasaexplorer.data.news.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsKind
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.RoomNewsLocalDataSource
import com.radustavila.nasaexplorer.data.news.remote.NewsFixtures
import com.radustavila.nasaexplorer.data.news.remote.RetrofitNewsRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.newsFeedService
import com.radustavila.nasaexplorer.data.news.remote.newsFeedUrls
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The news pipeline end to end - recorded feeds, real HTTP stack, real Room: refreshes replace one
 * feed each, items several feeds list appear once, and nothing unreadable replaces a good copy.
 */
@RunWith(RobolectricTestRunner::class)
class NewsRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider()
    private val favorites = FakeFavoritesRepository()
    private lateinit var database: NewsDatabase
    private lateinit var repository: NewsRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NewsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        repository = NewsRepositoryImpl(
            localDataSource = RoomNewsLocalDataSource(database.newsDao(), io),
            remoteDataSource = RetrofitNewsRemoteDataSource(newsFeedService(server), newsFeedUrls(server), io),
            favoritesRepository = favorites,
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    private fun itemWithLink(link: String) =
        Regex(
            """<item>\s*<title>[^<]*</title>\s*<link>${Regex.escape(link)}</link>.*?</item>""",
            RegexOption.DOT_MATCHES_ALL,
        )

    private fun serveEveryFeed() {
        server.route { request ->
            val feed = NewsFeed.entries.first { request.requestUrl!!.encodedPath == "/${it.key}" }
            server.fixtureResponse(NewsFixtures.BY_FEED.getValue(feed))
        }
    }

    @Test
    fun `each feed is fetched from its own url`() = runTest {
        server.enqueueFixture(NewsFixtures.YOUTUBE)

        assertEquals(DataResult.Success(15), repository.refresh(NewsFeed.YOUTUBE))
        assertEquals("/youtube", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `every feed refreshed gives one newest-first list without duplicates`() = runTest {
        serveEveryFeed()

        val counts = NewsFeed.entries.associateWith { (repository.refresh(it) as DataResult.Success).data }

        assertEquals(
            mapOf(
                NewsFeed.STORIES to 9,
                NewsFeed.NEWS_RELEASES to 8,
                NewsFeed.SCIENCE to 10,
                NewsFeed.IMAGE_OF_THE_DAY to 12,
                NewsFeed.YOUTUBE to 15,
            ),
            counts,
        )
        val news = repository.observeNews().first()
        // Releases repeat stories, and the Hubble story is in the science feed too.
        assertEquals(9 + 9 + 12 + 15, news.size)
        assertEquals(news.map { it.id }.distinct(), news.map { it.id })
        assertEquals(news.sortedByDescending { it.publishedAt }, news)
        val hubble = news.single { "hubble-charts-path" in it.id }
        assertEquals(NewsKind.SCIENCE, hubble.kind)
        assertEquals(NewsKind.STORY, news.single { "ncaa-flyover" in it.id }.kind)
    }

    @Test
    fun `an item is read by its id`() = runTest {
        server.enqueueFixture(NewsFixtures.IMAGE_OF_THE_DAY)
        repository.refresh(NewsFeed.IMAGE_OF_THE_DAY)

        val item = repository.observeItem("https://www.nasa.gov/image-detail/iss075e0190239/").first()

        assertEquals("Science on the Space Station with NASA Astronaut Jessica Watkins", item?.title)
        assertNull(repository.observeItem("https://www.nasa.gov/nothing/").first())
    }

    @Test
    fun `news stays readable offline after one refresh`() = runTest {
        server.enqueueFixture(NewsFixtures.SCIENCE)
        repository.refresh(NewsFeed.SCIENCE)
        server.enqueueStatus(503)

        val result = repository.refresh(NewsFeed.SCIENCE)

        assertEquals(503, ((result as DataResult.Failure).error as AppError.Http).code)
        assertEquals(10, repository.observeNews().first().size)
    }

    @Test
    fun `a broken document keeps the last good copy`() = runTest {
        server.enqueueFixture(NewsFixtures.YOUTUBE)
        repository.refresh(NewsFeed.YOUTUBE)
        server.enqueueFixture(NewsFixtures.TRUNCATED)

        val result = repository.refresh(NewsFeed.YOUTUBE)

        assertTrue((result as DataResult.Failure).error is AppError.Serialization)
        assertEquals(15, repository.observeNews().first().size)
    }

    @Test
    fun `a feed with nothing usable keeps the last good copy`() = runTest {
        server.enqueueFixture(NewsFixtures.STORIES)
        repository.refresh(NewsFeed.STORIES)
        server.enqueue(MockResponse().setBody("<rss><channel><item><title>no link</title></item></channel></rss>"))

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refresh(NewsFeed.STORIES))
        assertEquals(9, repository.observeNews().first().size)
    }

    @Test
    fun `a malformed item is dropped and the rest of the feed is stored`() = runTest {
        server.enqueueFixture(NewsFixtures.MALFORMED_ITEMS)

        assertEquals(DataResult.Success(1), repository.refresh(NewsFeed.SCIENCE))
    }

    @Test
    fun `a refresh replaces its feed but keeps favourite Images of the Day`() = runTest {
        server.enqueueFixture(NewsFixtures.IMAGE_OF_THE_DAY)
        repository.refresh(NewsFeed.IMAGE_OF_THE_DAY)
        val oldest = repository.observeNews().first().last()
        favorites.seed(FavoriteItem(FavoriteSource.NASA_IMAGE_OF_THE_DAY, oldest.id, oldest.title, oldest.fullImageUrl))
        val dropped = repository.observeNews().first().first { it.id != oldest.id }
        // The next day's feed: the same items minus one that is not a favourite, minus the favourite.
        val trimmed = listOf(dropped.id, oldest.id).fold(Fixtures.read(NewsFixtures.IMAGE_OF_THE_DAY)) { feed, id ->
            feed.replace(itemWithLink(id), "")
        }
        server.enqueue(MockResponse().setBody(trimmed))

        assertEquals(DataResult.Success(10), repository.refresh(NewsFeed.IMAGE_OF_THE_DAY))

        val ids = repository.observeNews().first().map { it.id }
        assertEquals(11, ids.size)
        assertTrue(oldest.id in ids)
        assertTrue(dropped.id !in ids)
    }
}
