package com.radustavila.nasaexplorer.data.forecast.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingScorer
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.StargazingRating
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.TestStargazing
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.forecast.local.ForecastDatabase
import com.radustavila.nasaexplorer.data.forecast.local.RoomForecastLocalDataSource
import com.radustavila.nasaexplorer.data.forecast.remote.MetNorwayRemoteDataSourceTest.Companion.LAST_MODIFIED
import com.radustavila.nasaexplorer.data.forecast.remote.MetNorwayRemoteDataSourceTest.Companion.RECORDED_HEADERS
import com.radustavila.nasaexplorer.data.forecast.remote.metNorwayRemoteDataSource
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The repository end to end: the recorded forecast through the real parser into an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class MetNorwayCloudForecastRepositoryTest {

    @get:Rule
    val server = FixtureServer()

    // When the fixture was recorded: 20:12:45 UTC, expiring at 20:42:51.
    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-22T20:12:45Z"))
    private val bucharest = GeoCoordinate(44.4268, 26.1025)
    private lateinit var database: ForecastDatabase
    private lateinit var repository: MetNorwayCloudForecastRepository

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ForecastDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MetNorwayCloudForecastRepository(
            // Not a test dispatcher: Room's flows would run on a scheduler other than runTest's.
            localDataSource = RoomForecastLocalDataSource(database.forecastDao(), Dispatchers.Unconfined),
            remoteDataSource = metNorwayRemoteDataSource(server),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `the recorded forecast is stored for the rounded place`() = runTest {
        assertNull(repository.observeForecast(bucharest).first())
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)

        assertEquals(DataResult.Success(Unit), repository.refreshForecast(bucharest))
        val forecast = repository.observeForecast(GeoCoordinate(44.431, 26.099)).first()!!

        assertEquals(GeoCoordinate(44.43, 26.1), forecast.coordinate)
        assertEquals(91, forecast.steps.size)
        assertEquals(Instant.parse("2026-10-22T20:00:00Z"), forecast.steps.first().time)
        assertEquals(78.9, forecast.steps.first().cloudCover, 0.0)
        assertEquals(11.7, forecast.steps.first().lowCloudCover!!, 0.0)
        assertEquals(0.0, forecast.steps.first().fogCover!!, 0.0)
        assertEquals(clock.currentInstant, forecast.fetchedAt)
        assertEquals(Instant.parse("2026-10-22T19:17:32Z"), forecast.updatedAt)
    }

    @Test
    fun `nothing is sent before Expires, then a 304 keeps the forecast until the new Expires`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)
        repository.refreshForecast(bucharest)

        clock.currentInstant = Instant.parse("2026-10-22T20:40:00Z")
        assertEquals(DataResult.Success(Unit), repository.refreshForecast(bucharest))
        assertEquals(1, server.requestCount)

        clock.currentInstant = Instant.parse("2026-10-22T20:43:00Z")
        server.enqueueStatus(304, headers = mapOf("Expires" to "Thu, 22 Oct 2026 21:13:00 GMT"))
        assertEquals(DataResult.Success(Unit), repository.refreshForecast(bucharest))
        server.takeRequest()
        assertEquals(LAST_MODIFIED, server.takeRequest().getHeader("If-Modified-Since"))
        val forecast = repository.observeForecast(bucharest).first()!!
        assertEquals(91, forecast.steps.size)
        assertEquals(clock.currentInstant, forecast.fetchedAt)

        clock.currentInstant = Instant.parse("2026-10-22T21:05:00Z")
        repository.refreshForecast(bucharest)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed or empty download keeps the last forecast`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)
        repository.refreshForecast(bucharest)
        clock.currentInstant = Instant.parse("2026-10-22T21:00:00Z")
        server.enqueueStatus(503)
        server.enqueueJson("""{"type":"Feature","properties":{"timeseries":[]}}""")

        assertEquals(DataResult.Failure(AppError.Http(503, "Server Error")), repository.refreshForecast(bucharest))
        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshForecast(bucharest))
        assertEquals(91, repository.observeForecast(bucharest).first()!!.steps.size)
    }

    @Test
    fun `a response without Expires waits the usual half hour`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json")
        repository.refreshForecast(bucharest)

        clock.currentInstant = clock.currentInstant.plusSeconds(29 * 60)
        repository.refreshForecast(bucharest)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the recorded overcast night in Bucharest scores poorly, a clear sky would not`() = runTest {
        server.enqueueFixture("metnorway/complete_bucharest.json", headers = RECORDED_HEADERS)
        repository.refreshForecast(bucharest)
        val forecast = repository.observeForecast(bucharest).first()!!
        // Bucharest on 22–23 October 2026: astronomically dark from about 16:50 to 03:10 UTC; it is 20:12.
        val night = TestStargazing.darkNight(darkStart = Instant.parse("2026-10-22T17:00:00Z"), darkHours = 10)

        val tonight = StargazingScorer.score(night, forecast, now = clock.currentInstant)
        val clear = StargazingScorer.score(
            night,
            forecast.copy(steps = forecast.steps.map { it.copy(cloudCover = 0.0, lowCloudCover = 0.0) }),
            now = clock.currentInstant,
        )

        assertEquals(StargazingRating.POOR, tonight.rating)
        assertNull(tonight.bestWindow)
        assertEquals(StargazingRating.EXCELLENT, clear.rating)
    }
}
