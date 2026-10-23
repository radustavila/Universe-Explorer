package com.radustavila.nasaexplorer.data.earthevents.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthquakesLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.RetrofitUsgsRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.UsgsFixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.usgsService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
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
 * The quake pipeline end to end - recorded feeds, real HTTP stack, real Room: both feeds merge,
 * repeats revalidate with `If-Modified-Since`, and failures keep the cache.
 */
@RunWith(RobolectricTestRunner::class)
class EarthquakeRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider()
    private lateinit var database: EarthEventsDatabase
    private lateinit var repository: EarthquakeRepositoryImpl
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** What each feed answers next, by path; a feed without an entry answers 304. */
    private val answers = ConcurrentHashMap<String, MockResponse>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EarthEventsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        repository = EarthquakeRepositoryImpl(
            localDataSource = RoomEarthquakesLocalDataSource(database.earthquakeDao(), io),
            remoteDataSource = RetrofitUsgsRemoteDataSource(usgsService(server), io),
            timeProvider = clock,
        )
        server.route { request ->
            requests += request
            answers.remove(request.requestUrl!!.encodedPath) ?: MockResponse().setResponseCode(HTTP_NOT_MODIFIED)
        }
    }

    @After
    fun tearDown() = database.close()

    private fun serveBothFeeds() {
        answers[WEEK_PATH] = server.fixtureResponse(UsgsFixtures.WEEK)
            .addHeader("Last-Modified", UsgsFixtures.LAST_MODIFIED)
        answers[SIGNIFICANT_PATH] = server.fixtureResponse(UsgsFixtures.SIGNIFICANT)
            .addHeader("Last-Modified", UsgsFixtures.LAST_MODIFIED)
    }

    @Test
    fun `a refresh merges both feeds, each quake once, newest first`() = runTest {
        serveBothFeeds()

        assertEquals(DataResult.Success(7), repository.refreshEarthquakes())

        val quakes = repository.observeEarthquakes().first()
        assertEquals(
            listOf("us6000u4bk", "us6000u4b3", "aka2026uzkyen", "us6000u43g", "us6000u3jl", "us6000u2y1", "us6000u2mq"),
            quakes.map { it.id },
        )
        assertEquals(setOf(WEEK_PATH, SIGNIFICANT_PATH), requests.map { it.requestUrl!!.encodedPath }.toSet())
        assertTrue(requests.all { it.getHeader("If-Modified-Since") == null })
    }

    @Test
    fun `a repeat revalidates with If-Modified-Since and a 304 keeps the cache`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        requests.clear()

        assertEquals(DataResult.Success(7), repository.refreshEarthquakes())

        assertEquals(2, requests.size)
        assertTrue(requests.all { it.getHeader("If-Modified-Since") == UsgsFixtures.LAST_MODIFIED })
        assertEquals(7, repository.observeEarthquakes().first().size)
    }

    @Test
    fun `a changed feed replaces only its own quakes`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        answers[WEEK_PATH] = server.fixtureResponse(UsgsFixtures.WEEK).setBody("""{"features":[]}""")

        repository.refreshEarthquakes()

        // The week's quakes left; Tibet stays through the significant feed, which answered 304.
        val ids = repository.observeEarthquakes().first().map { it.id }
        assertEquals(listOf("us6000u43g", "us6000u3jl", "us6000u2y1", "us6000u2mq"), ids)
    }

    @Test
    fun `one feed failing keeps its quakes and the refresh still succeeds`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        answers[WEEK_PATH] = MockResponse().setResponseCode(503)

        assertTrue(repository.refreshEarthquakes() is DataResult.Success)
        assertEquals(7, repository.observeEarthquakes().first().size)
    }

    @Test
    fun `both feeds failing is a failure and the cache stays`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        answers[WEEK_PATH] = MockResponse().setResponseCode(503)
        answers[SIGNIFICANT_PATH] = MockResponse().setResponseCode(503)

        val result = repository.refreshEarthquakes()

        assertEquals(503, ((result as DataResult.Failure).error as AppError.Http).code)
        assertEquals(7, repository.observeEarthquakes().first().size)
    }

    @Test
    fun `a feed of which nothing parses keeps the old quakes`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        answers[WEEK_PATH] = server.fixtureResponse(UsgsFixtures.WEEK).setBody("""{"features":[{"id":"x"}]}""")

        repository.refreshEarthquakes()

        assertEquals(7, repository.observeEarthquakes().first().size)
    }

    @Test
    fun `without cached quakes a feed is downloaded whole, not revalidated`() = runTest {
        serveBothFeeds()
        repository.refreshEarthquakes()
        database.earthquakeDao().deleteEarthquakesBefore(Long.MAX_VALUE)
        requests.clear()
        serveBothFeeds()

        repository.refreshEarthquakes()

        assertNull(requests.first().getHeader("If-Modified-Since"))
        assertEquals(7, repository.observeEarthquakes().first().size)
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val WEEK_PATH = "/summary/2.5_week.geojson"
        const val SIGNIFICANT_PATH = "/summary/significant_month.geojson"
    }
}
