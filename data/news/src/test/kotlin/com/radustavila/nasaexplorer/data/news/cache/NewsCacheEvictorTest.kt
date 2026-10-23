package com.radustavila.nasaexplorer.data.news.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.testing.data.TestNews
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.PodcastDownloadEntity
import com.radustavila.nasaexplorer.data.news.local.PodcastProgressEntity
import com.radustavila.nasaexplorer.data.news.local.RoomPodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.local.RoomNewsLocalDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NewsCacheEvictorTest {

    private val now = TestNews.NOW
    private val favorites = FakeFavoritesRepository()
    private lateinit var database: NewsDatabase
    private lateinit var local: RoomNewsLocalDataSource
    private lateinit var evictor: NewsCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NewsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        local = RoomNewsLocalDataSource(database.newsDao(), io)
        evictor = NewsCacheEvictor(database.newsDao(), database.podcastDao(), favorites, io)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `news not refreshed for a month goes, fresher news stays`() = runTest {
        val old = now.minus(NewsCacheEvictor.RETENTION).minusSeconds(1)
        local.replaceFeed(NewsFeed.STORIES, listOf(TestNews.story()), keepIds = emptySet(), at = old)
        local.replaceFeed(NewsFeed.YOUTUBE, listOf(TestNews.video()), keepIds = emptySet(), at = now)

        assertEquals(1, evictor.evict(now))

        assertEquals(listOf(TestNews.video().id), local.observeNews().first().map { it.id })
    }

    @Test
    fun `clearing the cache keeps favourite Images of the Day`() = runTest {
        val image = TestNews.imageOfTheDay()
        val other = TestNews.imageOfTheDay(id = "https://www.nasa.gov/image-detail/other/")
        local.replaceFeed(NewsFeed.IMAGE_OF_THE_DAY, listOf(image, other), keepIds = emptySet(), at = now)
        favorites.seed(FavoriteItem(FavoriteSource.NASA_IMAGE_OF_THE_DAY, image.id, image.title, image.fullImageUrl))

        // What "Clear cache" does: every retention period over.
        evictor.evict(now.plus(NewsCacheEvictor.RETENTION).plusSeconds(1))

        assertEquals(listOf(image.id), local.observeNews().first().map { it.id })
    }

    @Test
    fun `old episodes go except favourite and downloaded ones, and progress stays`() = runTest {
        val podcasts = RoomPodcastLocalDataSource(database.podcastDao(), UnconfinedTestDispatcher())
        val saved = TestPodcasts.houston()
        val downloaded = TestPodcasts.houston(id = "downloaded")
        val other = TestPodcasts.houston(id = "other")
        podcasts.replaceShow(saved.show, listOf(saved, downloaded, other), keepIds = emptySet(), at = now)
        favorites.seed(FavoriteItem(FavoriteSource.NASA_PODCAST_EPISODE, saved.id, saved.title, saved.imageUrl))
        database.podcastDao().upsertDownload(
            PodcastDownloadEntity(downloaded.id, PodcastDownloadState.DOWNLOADED.name, "a.audio", 1, 0),
        )
        database.podcastDao().upsertProgress(PodcastProgressEntity(other.id, 60_000, isFinished = false, 0))

        evictor.evict(now.plus(NewsCacheEvictor.RETENTION).plusSeconds(1))

        assertEquals(setOf(saved.id, downloaded.id), podcasts.observeEpisodes().first().map { it.id }.toSet())
        assertEquals(1, database.query("SELECT * FROM podcast_progress", null).use { it.count })
    }
}
