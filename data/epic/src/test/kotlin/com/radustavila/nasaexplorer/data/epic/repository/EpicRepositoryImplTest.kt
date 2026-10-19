package com.radustavila.nasaexplorer.data.epic.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.data.epic.RecordedResponses
import com.radustavila.nasaexplorer.data.epic.local.RoomEpicLocalDataSource
import com.radustavila.nasaexplorer.data.epic.prefetch.ImagePrefetcher
import com.radustavila.nasaexplorer.data.epic.remote.EpicService
import com.radustavila.nasaexplorer.data.epic.remote.RetrofitEpicRemoteDataSource
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The offline-first contract end to end below the domain: recorded EPIC responses served by
 * MockWebServer, parsed by the real HTTP stack and written into a real Room database, then read
 * back the way the screens read them.
 */
@RunWith(RobolectricTestRunner::class)
class EpicRepositoryImplTest {

    private lateinit var database: NasaDatabase
    private lateinit var server: MockWebServer
    private lateinit var repository: EpicRepositoryImpl

    private val prefetcher = RecordingPrefetcher()

    /** Response bodies by request path; anything else is a 404. */
    private val responses = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                responses[request.requestUrl?.encodedPath]
                    ?.let { body -> MockResponse().setHeader("Content-Type", "application/json").setBody(body) }
                    ?: MockResponse().setResponseCode(404)
        }
        server.start()

        val service = RetrofitFactory(
            json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
            callFactory = OkHttpClient(),
        ).create(server.url("/").toString()).create(EpicService::class.java)

        val dispatcher = UnconfinedTestDispatcher()
        repository = EpicRepositoryImpl(
            localDataSource = RoomEpicLocalDataSource(database.epicDao(), dispatcher),
            remoteDataSource = RetrofitEpicRemoteDataSource(service, dispatcher),
            imagePrefetcher = prefetcher,
            timeProvider = FakeTimeProvider(),
            archiveBaseUrl = ARCHIVE_BASE_URL,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    @Test
    fun `the first refresh caches the newest day and the archive listing`() = runTest {
        responses[LATEST] = RecordedResponses.NATURAL_LATEST
        responses[ALL] = RecordedResponses.NATURAL_ALL

        val result = repository.refreshLatest(EpicCollection.NATURAL)

        assertEquals(DataResult.Success(13), result)
        val latest = repository.observeLatestDay(EpicCollection.NATURAL).first()
        assertEquals(13, latest.size)
        assertEquals(LATEST_DAY, latest.first().date)
        assertEquals(latest.sortedBy(EpicImage::capturedAt), latest)
        assertEquals(LATEST_DAY, repository.observeAvailableDates(EpicCollection.NATURAL).first().first())
    }

    @Test
    fun `the archive listing is fetched again only when a new day appears`() = runTest {
        responses[LATEST] = RecordedResponses.NATURAL_LATEST
        responses[ALL] = RecordedResponses.NATURAL_ALL
        repository.refreshLatest(EpicCollection.NATURAL)
        val requestsAfterFirstSync = server.requestCount

        repository.refreshLatest(EpicCollection.NATURAL)

        // Same newest day, already listed: one request for the day, none for the listing.
        assertEquals(requestsAfterFirstSync + 1, server.requestCount)
    }

    @Test
    fun `a refresh keeps the last week and drops older days`() = runTest {
        responses["/api/natural/date/2026-10-12"] = dayJson("2026-10-12")
        responses["/api/natural/date/2026-10-01"] = dayJson("2026-10-01")
        repository.fetchDay(EpicCollection.NATURAL, LocalDate.of(2026, 10, 12))
        repository.fetchDay(EpicCollection.NATURAL, LocalDate.of(2026, 10, 1))
        responses[LATEST] = RecordedResponses.NATURAL_LATEST
        responses[ALL] = RecordedResponses.NATURAL_ALL

        repository.refreshLatest(EpicCollection.NATURAL)

        assertEquals(1, repository.observeDay(EpicCollection.NATURAL, LocalDate.of(2026, 10, 12)).first().size)
        assertTrue(repository.observeDay(EpicCollection.NATURAL, LocalDate.of(2026, 10, 1)).first().isEmpty())
    }

    @Test
    fun `an archive day is fetched into the cache and observed from it`() = runTest {
        responses["/api/enhanced/date/2026-09-28"] = dayJson("2026-09-28", prefix = "epic_RGB")

        val result = repository.fetchDay(EpicCollection.ENHANCED, LocalDate.of(2026, 9, 28))

        assertEquals(DataResult.Success(1), result)
        val day = repository.observeDay(EpicCollection.ENHANCED, LocalDate.of(2026, 9, 28)).first()
        assertEquals(
            "${ARCHIVE_BASE_URL}archive/enhanced/2026/09/28/thumbs/epic_RGB_20260928001752.jpg",
            day.single().thumbnailUrl,
        )
    }

    @Test
    fun `a day without photos is an empty response and leaves the cache untouched`() = runTest {
        responses[LATEST] = "[]"
        responses["/api/natural/date/2026-10-09"] = "[]"

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshLatest(EpicCollection.NATURAL))
        assertEquals(
            DataResult.Failure(AppError.EmptyResponse),
            repository.fetchDay(EpicCollection.NATURAL, LocalDate.of(2026, 10, 9)),
        )
        assertTrue(repository.observeLatestDay(EpicCollection.NATURAL).first().isEmpty())
    }

    @Test
    fun `a failed archive listing fails the refresh so the sync retries it`() = runTest {
        responses[LATEST] = RecordedResponses.NATURAL_LATEST

        val result = repository.refreshLatest(EpicCollection.NATURAL)

        assertEquals(404, ((result as DataResult.Failure).error as AppError.Http).code)
        // The photos themselves are already safe in the cache.
        assertEquals(13, repository.observeLatestDay(EpicCollection.NATURAL).first().size)
    }

    @Test
    fun `prefetching warms the thumbnails of the newest day`() = runTest {
        responses[LATEST] = RecordedResponses.NATURAL_LATEST
        responses[ALL] = RecordedResponses.NATURAL_ALL
        repository.refreshLatest(EpicCollection.NATURAL)

        repository.prefetchLatestThumbnails(EpicCollection.NATURAL)

        assertEquals(13, prefetcher.urls.size)
        assertTrue(prefetcher.urls.all { it.contains("/archive/natural/2026/10/16/thumbs/") })
    }

    private fun dayJson(date: String, prefix: String = "epic_1b"): String {
        val stamp = date.replace("-", "") + "001752"
        return """[{"identifier":"$stamp","caption":"","image":"${prefix}_$stamp","date":"$date 00:13:03",""" +
            """"centroid_coordinates":{"lat":1.3,"lon":166.5}}]"""
    }

    private class RecordingPrefetcher : ImagePrefetcher {
        val urls = mutableListOf<String>()
        override suspend fun prefetch(urls: List<String>) {
            this.urls += urls
        }
    }

    private companion object {
        const val ARCHIVE_BASE_URL = "https://epic.gsfc.nasa.gov/"
        const val LATEST = "/api/natural"
        const val ALL = "/api/natural/all"
        val LATEST_DAY: LocalDate = LocalDate.of(2026, 10, 16)
    }
}
