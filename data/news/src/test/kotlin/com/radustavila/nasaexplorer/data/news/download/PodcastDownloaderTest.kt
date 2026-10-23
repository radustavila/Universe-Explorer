package com.radustavila.nasaexplorer.data.news.download

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.news.local.NewsDatabase
import com.radustavila.nasaexplorer.data.news.local.PodcastDownloadEntity
import com.radustavila.nasaexplorer.data.news.local.RoomPodcastLocalDataSource
import com.radustavila.nasaexplorer.data.news.remote.PodcastRemoteDataSource
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedDocumentDto
import com.radustavila.nasaexplorer.data.news.repository.PodcastRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Downloads against a local server shaped like Curious Universe's enclosures: a tracking redirect,
 * then Megaphone's own redirect to its CDN, then the audio.
 */
@RunWith(RobolectricTestRunner::class)
class PodcastDownloaderTest {

    @get:Rule
    val server = FixtureServer()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeTimeProvider()
    private val favorites = FakeFavoritesRepository()
    private val io = UnconfinedTestDispatcher()
    private val audio = ByteArray(200_000) { (it % 251).toByte() }
    private lateinit var database: NewsDatabase
    private lateinit var local: RoomPodcastLocalDataSource
    private lateinit var files: PodcastFiles
    private lateinit var downloader: PodcastDownloader

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, NewsDatabase::class.java).allowMainThreadQueries().build()
        local = RoomPodcastLocalDataSource(database.podcastDao(), io)
        files = PodcastFiles(context.filesDir.resolve("podcasts-test"))
        downloader = PodcastDownloader(database.podcastDao(), files, clock, { OkHttpClient() }, io)
    }

    @After
    fun tearDown() {
        database.close()
        files.root.deleteRecursively()
    }

    /** Caches [episode] with its audio on the local server and asks for its download. */
    private suspend fun wanted(episode: PodcastEpisode = TestPodcasts.curiousUniverse()): String {
        val local = episode.copy(audioUrl = "${server.baseUrl}track/477F33/traffic/episode.mp3")
        this.local.replaceShow(local.show, listOf(local), keepIds = emptySet(), at = clock.now())
        database.podcastDao().upsertDownload(
            PodcastDownloadEntity(local.id, PodcastDownloadState.QUEUED.name, filePath = null, bytes = 0, 0),
        )
        return local.id
    }

    private fun serveRedirectedAudio() {
        server.route { request ->
            when (request.requestUrl!!.encodedPath) {
                "/track/477F33/traffic/episode.mp3" ->
                    MockResponse().setResponseCode(HTTP_FOUND).setHeader("Location", "/traffic/episode.mp3")

                "/traffic/episode.mp3" ->
                    MockResponse().setResponseCode(HTTP_FOUND).setHeader("Location", "/cdn/episode.mp3?key=abc")

                "/cdn/episode.mp3" -> MockResponse().setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(audio))

                else -> MockResponse().setResponseCode(HTTP_NOT_FOUND)
            }
        }
    }

    @Test
    fun `the audio is followed through both redirects and kept`() = runTest {
        serveRedirectedAudio()
        val id = wanted()

        assertEquals(PodcastDownloadOutcome.SAVED, downloader.download(id))

        val row = database.podcastDao().findDownload(id)!!
        assertEquals(PodcastDownloadState.DOWNLOADED.name, row.state)
        assertEquals(audio.size.toLong(), row.bytes)
        assertTrue(files.resolve(row.filePath!!).readBytes().contentEquals(audio))
        assertFalse(files.partialFileFor(id).exists())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a server error is retried later, a missing file fails`() = runTest {
        server.route { MockResponse().setResponseCode(HTTP_UNAVAILABLE) }
        val id = wanted()
        assertEquals(PodcastDownloadOutcome.RETRY_LATER, downloader.download(id))
        assertEquals(PodcastDownloadState.QUEUED.name, database.podcastDao().findDownload(id)!!.state)

        server.route { MockResponse().setResponseCode(HTTP_NOT_FOUND) }
        assertEquals(PodcastDownloadOutcome.FAILED, downloader.download(id))
        assertEquals(PodcastDownloadState.FAILED.name, database.podcastDao().findDownload(id)!!.state)
    }

    @Test
    fun `something other than audio is not kept`() = runTest {
        server.route { MockResponse().setHeader("Content-Type", "text/html").setBody("<html>sign in</html>") }
        val id = wanted()

        assertEquals(PodcastDownloadOutcome.FAILED, downloader.download(id))
        assertEquals(0L, files.sizeInBytes())
    }

    @Test
    fun `a cancelled download is not needed`() = runTest {
        serveRedirectedAudio()
        val id = wanted()
        database.podcastDao().deleteDownload(id)

        assertEquals(PodcastDownloadOutcome.NOT_NEEDED, downloader.download(id))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `clearing the cache deletes downloads except favourites', and Settings measures them`() = runTest {
        serveRedirectedAudio()
        val kept = wanted(TestPodcasts.curiousUniverse())
        val other = wanted(TestPodcasts.houston())
        downloader.download(kept)
        downloader.download(other)
        favorites.save(FavoriteItem(FavoriteSource.NASA_PODCAST_EPISODE, kept, "Kept", null, clock.now()))
        val storage = PodcastDownloadStorage(files, database.podcastDao(), repository(), favorites, io)
        assertEquals(2L * audio.size, storage.measure().single { it.kind == StorageKind.PODCAST_DOWNLOADS }.bytes)

        storage.clear()

        assertEquals(audio.size.toLong(), storage.measure().single().bytes)
        assertEquals(listOf(kept), database.podcastDao().downloads().map { it.episodeId })
        assertNull(local.observeEpisode(other).first()!!.download)
    }

    private fun repository(): PodcastRepositoryImpl {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        return PodcastRepositoryImpl(
            localDataSource = local,
            remoteDataSource = object : PodcastRemoteDataSource {
                override suspend fun fetch(show: PodcastShow): DataResult<FeedDocumentDto> =
                    DataResult.Failure(AppError.NoConnection)
            },
            favoritesRepository = favorites,
            files = files,
            workManager = { WorkManager.getInstance(context) },
            timeProvider = clock,
        )
    }

    private companion object {
        const val HTTP_FOUND = 302
        const val HTTP_NOT_FOUND = 404
        const val HTTP_UNAVAILABLE = 503
    }
}
