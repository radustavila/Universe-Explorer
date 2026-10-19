package com.radustavila.nasaexplorer.data.nasaimages.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesDatabase
import com.radustavila.nasaexplorer.data.nasaimages.local.RoomNasaImagesLocalDataSource
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesFixtures
import com.radustavila.nasaexplorer.data.nasaimages.remote.RetrofitNasaImagesRemoteDataSource
import com.radustavila.nasaexplorer.data.nasaimages.remote.nasaImagesService
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
 * The library pipeline end to end - recorded responses, real HTTP stack, real Room: rails are cached
 * and survive any failure, search stays in memory, saved items persist.
 */
@RunWith(RobolectricTestRunner::class)
class NasaMediaRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider()
    private lateinit var database: NasaImagesDatabase
    private lateinit var repository: NasaMediaRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaImagesDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = NasaMediaRepositoryImpl(
            localDataSource = RoomNasaImagesLocalDataSource(database.nasaMediaDao(), io),
            remoteDataSource = RetrofitNasaImagesRemoteDataSource(nasaImagesService(server), io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    private fun enqueueEveryRail() {
        repeat(GalleryRail.entries.size) { server.enqueueFixture(NasaImagesFixtures.WEBB_GALAXIES) }
    }

    @Test
    fun `a refresh caches every rail, in rail order, one call each`() = runTest {
        enqueueEveryRail()

        val result = repository.refreshRails()

        assertEquals(DataResult.Success(Unit), result)
        assertEquals(GalleryRail.entries.size, server.requestCount)
        val rails = repository.observeRails().first()
        assertEquals(GalleryRail.entries, rails.keys.toList())
        assertEquals("PIA26702", rails.getValue(GalleryRail.WEBB_GALAXIES).first().nasaId)
        assertEquals(5, rails.getValue(GalleryRail.MILKY_WAY).size)
    }

    @Test
    fun `cached rails are served with the network gone`() = runTest {
        enqueueEveryRail()
        repository.refreshRails()
        val cached = repository.observeRails().first()

        server.enqueueStatus(503)
        val result = repository.refreshRails()

        assertTrue(result is DataResult.Failure)
        assertEquals(cached, repository.observeRails().first())
    }

    @Test
    fun `one failing rail leaves every cached rail untouched`() = runTest {
        enqueueEveryRail()
        repository.refreshRails()
        val cached = repository.observeRails().first()

        repeat(GalleryRail.entries.size - 1) { server.enqueueFixture(NasaImagesFixtures.EMPTY) }
        server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "120"))
        val result = repository.refreshRails()

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 120)), result)
        assertEquals(cached, repository.observeRails().first())
    }

    @Test
    fun `a refresh with nothing usable keeps the cached rails`() = runTest {
        enqueueEveryRail()
        repository.refreshRails()
        val cached = repository.observeRails().first()

        repeat(GalleryRail.entries.size) { server.enqueueFixture(NasaImagesFixtures.EMPTY) }

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshRails())
        assertEquals(cached, repository.observeRails().first())
    }

    @Test
    fun `observers see the rails arrive`() = runTest {
        repository.observeRails().test {
            assertTrue(awaitItem().isEmpty())

            enqueueEveryRail()
            repository.refreshRails()

            assertEquals(GalleryRail.entries.size, awaitItem().size)
        }
    }

    @Test
    fun `a search page is served from memory while fresh`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.MIXED_PAGE_2)
        val query = NasaMediaQuery(text = "webb")

        val first = repository.search(query, page = 2)
        val second = repository.search(query, page = 2)

        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a stale search page is fetched again`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.MIXED_PAGE_2)
        server.enqueueFixture(NasaImagesFixtures.MIXED_PAGE_2)
        val query = NasaMediaQuery(text = "webb")

        repository.search(query, page = 2)
        clock.currentInstant = clock.currentInstant.plus(Duration.ofMinutes(11))
        repository.search(query, page = 2)

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed search caches nothing`() = runTest {
        server.enqueueStatus(503)
        server.enqueueFixture(NasaImagesFixtures.EMPTY)
        val query = NasaMediaQuery(text = "zzz")

        assertTrue(repository.search(query, page = 1) is DataResult.Failure)
        assertTrue(repository.search(query, page = 1) is DataResult.Success)
    }

    @Test
    fun `a search result opens from memory but is not stored`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.VIDEOS)
        repository.search(NasaMediaQuery(text = "webb"), page = 1)

        val found = repository.observeMedia("Webb Journey to Space Ep2").first()

        assertEquals("Webb Journey to Space Ep2", found?.title)
        assertEquals(0, database.nasaMediaDao().count())
    }

    @Test
    fun `a kept item is stored and opens without the network`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.VIDEOS)
        val media = (repository.search(NasaMediaQuery(text = "webb"), 1) as DataResult.Success).data.items.first()

        repository.keep(media)

        assertEquals(1, database.nasaMediaDao().count())
        // A fresh repository has no memory of the search: the item comes from Room.
        val restarted = NasaMediaRepositoryImpl(
            localDataSource = RoomNasaImagesLocalDataSource(database.nasaMediaDao(), UnconfinedTestDispatcher()),
            remoteDataSource = RetrofitNasaImagesRemoteDataSource(nasaImagesService(server), UnconfinedTestDispatcher()),
            timeProvider = clock,
        )
        assertEquals(media, restarted.observeMedia(media.nasaId).first())
    }

    @Test
    fun `an unknown item is fetched by id`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.BY_ID)

        repository.observeMedia("PIA26702").test {
            assertNull(awaitItem())

            val result = repository.fetch("PIA26702")

            assertEquals("Webb Data Reveals Dark Matter", (result as DataResult.Success).data.title)
            assertEquals(result.data, awaitItem())
        }
    }

    @Test
    fun `an id the library does not know is an empty response`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.EMPTY)

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.fetch("nothing"))
    }

    @Test
    fun `a playback url is resolved once per item`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.VIDEOS)
        server.enqueueFixture(NasaImagesFixtures.VIDEO_ASSETS)
        val video = (repository.search(NasaMediaQuery(text = "webb"), 1) as DataResult.Success).data.items.first()

        val first = repository.resolvePlaybackUrl(video)
        val second = repository.resolvePlaybackUrl(video)

        assertTrue((first as DataResult.Success).data.endsWith("~medium.mp4"))
        assertEquals(first, second)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an item with no playable file is an empty response`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.VIDEOS)
        server.enqueueFixture(NasaImagesFixtures.IMAGE_ASSETS)
        val video = (repository.search(NasaMediaQuery(text = "webb"), 1) as DataResult.Success).data.items.first()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.resolvePlaybackUrl(video))
    }
}
