package com.radustavila.nasaexplorer.data.spaceweather.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.KpKind
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.ConcurrencyGate
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.spaceweather.SpaceWeatherFixtures
import com.radustavila.nasaexplorer.data.spaceweather.donkiService
import com.radustavila.nasaexplorer.data.spaceweather.local.RoomSpaceWeatherLocalDataSource
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDatabase
import com.radustavila.nasaexplorer.data.spaceweather.remote.RetrofitSpaceWeatherRemoteDataSource
import com.radustavila.nasaexplorer.data.spaceweather.swpcService
import java.time.Instant
import java.time.LocalDate
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

/** Recorded NOAA and DONKI answers through the real HTTP stack into real Room. */
@RunWith(RobolectricTestRunner::class)
class SpaceWeatherRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(
        currentInstant = Instant.parse("2026-10-19T14:20:00Z"),
        currentDate = LocalDate.of(2026, 10, 19),
    )
    private lateinit var database: SpaceWeatherDatabase
    private lateinit var repository: SpaceWeatherRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SpaceWeatherDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = SpaceWeatherRepositoryImpl(
            local = RoomSpaceWeatherLocalDataSource(database.spaceWeatherDao(), io),
            remote = RetrofitSpaceWeatherRemoteDataSource(swpcService(server), donkiService(server), io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    /** Calls of one refresh go out at once, so they are answered by path, not in order. */
    private fun route(vararg answers: Pair<String, MockResponse>, gate: ConcurrencyGate? = null) {
        server.route { request ->
            gate?.awaitOthers()
            answers.firstOrNull { (prefix, _) -> request.path.orEmpty().startsWith(prefix) }?.second
                ?: MockResponse().setResponseCode(404)
        }
    }

    private fun fixture(path: String) = server.fixtureResponse(path)

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private fun routeKp(
        forecast: MockResponse = fixture(SpaceWeatherFixtures.KP_FORECAST),
        oneMinute: MockResponse = fixture(SpaceWeatherFixtures.KP_ONE_MINUTE),
        gate: ConcurrencyGate? = null,
    ) = route(KP_FORECAST_PATH to forecast, KP_ONE_MINUTE_PATH to oneMinute, gate = gate)

    private fun routeSolar(flares: MockResponse, cmes: MockResponse, gate: ConcurrencyGate? = null) =
        route("/get/FLR" to flares, "/get/CME" to cmes, gate = gate)

    @Test
    fun `the forecast and the 1-minute estimate are asked together, and so are flares and CMEs`() = runTest {
        val kpGate = ConcurrencyGate(requests = 2)
        routeKp(gate = kpGate)
        assertEquals(DataResult.Success(Unit), repository.refreshKp())

        val solarGate = ConcurrencyGate(requests = 2)
        routeSolar(fixture(SpaceWeatherFixtures.FLARES), fixture(SpaceWeatherFixtures.CMES), gate = solarGate)
        assertEquals(DataResult.Success(Unit), repository.refreshSolarActivity())

        assertTrue(kpGate.allArrivedTogether)
        assertTrue(solarGate.allArrivedTogether)
    }

    @Test
    fun `Kp refresh caches the 3-hour slots and the latest 1-minute estimate`() = runTest {
        routeKp()

        assertEquals(DataResult.Success(Unit), repository.refreshKp())

        val forecast = repository.observeKpForecast().first()
        assertEquals(81, forecast.size)
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), forecast.first().start)
        assertEquals(1.67, forecast.first().kp, 0.0)
        assertEquals(KpKind.OBSERVED, forecast.first().kind)
        assertEquals(KpKind.PREDICTED, forecast.last().kind)
        assertEquals(KpKind.ESTIMATED, forecast.first { it.start == Instant.parse("2026-10-19T12:00:00Z") }.kind)

        val current = repository.observeCurrentKp().first()
        assertEquals(Instant.parse("2026-10-19T14:16:00Z"), current?.time)
        assertEquals(0.33, current!!.kp, 0.0)

        assertEquals(setOf(KP_FORECAST_PATH, KP_ONE_MINUTE_PATH), List(2) { server.takeRequest().path }.toSet())
    }

    @Test
    fun `a failed forecast keeps the cache, a failed 1-minute estimate does not fail the refresh`() = runTest {
        routeKp()
        repository.refreshKp()

        routeKp(forecast = status(503))
        val failed = repository.refreshKp()
        assertEquals(503, ((failed as DataResult.Failure).error as AppError.Http).code)
        assertEquals(81, repository.observeKpForecast().first().size)

        routeKp(oneMinute = status(500))
        assertEquals(DataResult.Success(Unit), repository.refreshKp())
        assertEquals(0.33, repository.observeCurrentKp().first()!!.kp, 0.0)
    }

    @Test
    fun `an answer without a usable slot is an empty response`() = runTest {
        routeKp(forecast = json("""[{"time_tag":"not a time","kp":3.0,"observed":"observed"}]"""))

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshKp())
    }

    @Test
    fun `the aurora grid is packed into the cache with its times`() = runTest {
        server.enqueueFixture(SpaceWeatherFixtures.AURORA)

        assertEquals(DataResult.Success(Unit), repository.refreshAuroraForecast())

        val forecast = repository.observeAuroraForecast().first()!!
        assertEquals(Instant.parse("2026-10-19T14:08:00Z"), forecast.observedAt)
        assertEquals(Instant.parse("2026-10-19T15:20:00Z"), forecast.forecastFor)
        // [0, -88, 2] and [359, 74, 6] in the recorded file; longitude 180 was trimmed away.
        assertEquals(2, forecast.probabilityAt(longitude = 0, latitude = -88))
        assertEquals(6, forecast.probabilityAt(longitude = 359, latitude = 74))
        assertEquals(6, forecast.probabilityAt(longitude = -1, latitude = 74))
        assertEquals(0, forecast.probabilityAt(longitude = 180, latitude = 74))
        assertEquals(clock.currentInstant, repository.auroraForecastFetchedAt())
    }

    @Test
    fun `solar activity keeps the latest flare and CME, asking DONKI for both dates`() = runTest {
        routeSolar(flares = fixture(SpaceWeatherFixtures.FLARES), cmes = fixture(SpaceWeatherFixtures.CMES))

        assertEquals(DataResult.Success(Unit), repository.refreshSolarActivity())

        val flare = repository.observeLatestFlare().first()!!
        assertEquals("M1.6", flare.classType)
        assertEquals('M', flare.classLetter)
        assertEquals(Instant.parse("2026-10-19T08:54:00Z"), flare.beginTime)
        assertEquals(14551, flare.activeRegion)
        val cme = repository.observeLatestCme().first()!!
        assertEquals("2026-10-18T23:15:00-CME-001", cme.id)
        assertEquals(590.0, cme.speedKmPerSecond!!, 0.0)
        assertNull(cme.earthArrival)

        assertEquals(
            setOf("/get/FLR?startDate=2026-09-19&endDate=2026-10-19", "/get/CME?startDate=2026-10-05&endDate=2026-10-19"),
            List(2) { server.takeRequest().path }.toSet(),
        )
    }

    @Test
    fun `a quiet Sun is not an error`() = runTest {
        routeSolar(flares = json("[]"), cmes = json("[]"))

        assertEquals(DataResult.Success(Unit), repository.refreshSolarActivity())
        assertNull(repository.observeLatestFlare().first())
    }

    @Test
    fun `a failed CME call stores nothing`() = runTest {
        routeSolar(
            flares = fixture(SpaceWeatherFixtures.FLARES),
            cmes = json("""{"status":400,"error":"Bad Request"}""", code = 400),
        )

        assertTrue(repository.refreshSolarActivity() is DataResult.Failure)
        assertNull(repository.observeLatestFlare().first())
    }

    private companion object {
        const val KP_FORECAST_PATH = "/products/noaa-planetary-k-index-forecast.json"
        const val KP_ONE_MINUTE_PATH = "/json/planetary_k_index_1m.json"
    }
}
