package com.radustavila.nasaexplorer.data.news.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastProgress
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.news.download.PodcastDownloadWorker
import com.radustavila.nasaexplorer.data.news.download.PodcastFiles
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.RoomPodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.remote.RetrofitPodcastRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.feedResponse
import com.radustavila.nasaexplorer.data.news.remote.newsFeedService
import com.radustavila.nasaexplorer.data.news.remote.podcastFeedUrls
import com.radustavila.nasaexplorer.data.news.remote.servePodcasts
import java.time.Duration
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
 * The podcast pipeline end to end - recorded Megaphone feeds behind nasa.gov's redirect, real HTTP
 * stack, real Room: refreshes replace one show each, and progress, downloads and favourites
 * survive them.
 */
@RunWith(RobolectricTestRunner::class)
class PodcastRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeTimeProvider()
    private val favorites = FakeFavoritesRepository()
    private lateinit var database: NewsDatabase
    private lateinit var files: PodcastFiles
    private lateinit var workManager: WorkManager
    private lateinit var repository: PodcastRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, NewsDatabase::class.java).allowMainThreadQueries().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        files = PodcastFiles(context.filesDir.resolve("podcasts-test"))
        val io = UnconfinedTestDispatcher()
        repository = PodcastRepositoryImpl(
            localDataSource = RoomPodcastLocalDataSource(database.podcastDao(), io),
            remoteDataSource = RetrofitPodcastRemoteDataSource(newsFeedService(server), podcastFeedUrls(server), io),
            favoritesRepository = favorites,
            files = files,
            workManager = { workManager },
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
        files.root.deleteRecursively()
    }

    @Test
    fun `every show is read through nasa_gov's redirect, newest episode first`() = runTest {
        server.servePodcasts()

        PodcastShow.entries.forEach { show -> assertTrue(repository.refresh(show) is DataResult.Success) }

        val episodes = repository.observeEpisodes().first()
        assertEquals(17, episodes.size)
        assertEquals(episodes.sortedByDescending { it.publishedAt }, episodes)
        assertEquals("Encore: A Year In Mars Dune Alpha", episodes.first().title)
        assertEquals(PodcastShow.entries.toSet(), episodes.map { it.show }.toSet())
        assertTrue(episodes.first().audioUrl.startsWith("https://chrt.fm/track/"))
    }

    @Test
    fun `progress and downloads survive a refresh, and a downloaded episode the feed dropped stays`() = runTest {
        server.servePodcasts()
        repository.refresh(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST)
        val (newest, oldest) = repository.observeEpisodes().first().let { it.first() to it.last() }
        repository.saveProgress(newest.id, PodcastProgress(Duration.ofMinutes(12)))
        repository.requestDownload(oldest.id)

        // The next refresh lists only the newest episode.
        server.servePodcasts { show ->
            val body = feedResponse(show).getBody()!!.readUtf8()
            val firstItemEnd = body.indexOf("</item>") + "</item>".length
            MockResponse().setHeader("Content-Type", "application/xml")
                .setBody(body.substring(0, firstItemEnd) + "</channel></rss>")
        }
        assertEquals(DataResult.Success(1), repository.refresh(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST))

        val episodes = repository.observeEpisodes().first()
        assertEquals(listOf(newest.id, oldest.id), episodes.map { it.id })
        assertEquals(PodcastProgress(Duration.ofMinutes(12)), episodes.first().progress)
        assertEquals(PodcastDownloadState.QUEUED, episodes.last().download?.state)
    }

    @Test
    fun `a favourite episode the feed dropped stays`() = runTest {
        server.servePodcasts()
        repository.refresh(PodcastShow.SMALL_STEPS_GIANT_LEAPS)
        val oldest = repository.observeEpisodes().first().last()
        favorites.save(FavoriteItem(FavoriteSource.NASA_PODCAST_EPISODE, oldest.id, oldest.title, null, clock.now()))

        server.servePodcasts { show ->
            val body = feedResponse(show).getBody()!!.readUtf8()
            MockResponse().setBody(body.substring(0, body.indexOf("</item>") + 7) + "</channel></rss>")
        }
        repository.refresh(PodcastShow.SMALL_STEPS_GIANT_LEAPS)

        assertEquals(oldest.id, repository.observeEpisodes().first().last().id)
    }

    @Test
    fun `a feed with nothing usable or a broken document keeps the last good copy`() = runTest {
        server.servePodcasts()
        repository.refresh(PodcastShow.CURIOUS_UNIVERSE)

        server.servePodcasts { MockResponse().setBody("<rss><channel><item><title>x</title></item></channel></rss>") }
        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refresh(PodcastShow.CURIOUS_UNIVERSE))

        server.servePodcasts { MockResponse().setBody("<rss><channel><item><title>cut") }
        assertTrue((repository.refresh(PodcastShow.CURIOUS_UNIVERSE) as DataResult.Failure).error is AppError.Serialization)

        assertEquals(6, repository.observeEpisodes().first().size)
    }

    @Test
    fun `a download is queued as unique work and removing it cancels the work and the row`() = runTest {
        server.servePodcasts()
        repository.refresh(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST)
        val episode = repository.observeEpisodes().first().first()

        repository.requestDownload(episode.id)
        val work = workManager.getWorkInfosForUniqueWork(PodcastDownloadWorker.uniqueWorkName(episode.id)).get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
        assertNull(repository.downloadedAudioPath(episode.id))

        repository.removeDownload(episode.id)
        val cancelled = workManager.getWorkInfosForUniqueWork(PodcastDownloadWorker.uniqueWorkName(episode.id)).get()
        assertEquals(WorkInfo.State.CANCELLED, cancelled.single().state)
        assertNull(repository.observeEpisode(episode.id).first()!!.download)
    }

    @Test
    fun `the downloaded file's path is given only once it is on disk`() = runTest {
        server.servePodcasts()
        repository.refresh(PodcastShow.HOUSTON_WE_HAVE_A_PODCAST)
        val episode = repository.observeEpisodes().first().first()
        repository.requestDownload(episode.id)
        val relative = files.relativePathFor(episode.id)
        database.podcastDao().updateDownload(episode.id, PodcastDownloadState.DOWNLOADED.name, relative, 3, 0)
        assertNull(repository.downloadedAudioPath(episode.id))

        files.resolve(relative).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))

        assertEquals(files.resolve(relative).absolutePath, repository.downloadedAudioPath(episode.id))
    }
}
