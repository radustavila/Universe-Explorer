package com.radustavila.nasaexplorer.data.spaceweather.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.KpKind
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
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

    @Test
    fun `Kp refresh caches the 3-hour slots and the latest 1-minute estimate`() = runTest {
        server.enqueueFixture(SpaceWeatherFixtures.KP_FORECAST)
        server.enqueueFixture(SpaceWeatherFixtures.KP_ONE_MINUTE)

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

        assertEquals("/products/noaa-planetary-k-index-forecast.json", server.takeRequest().path)
        assertEquals("/json/planetary_k_index_1m.json", server.takeRequest().path)
    }

    @Test
    fun `a failed forecast keeps the cache, a failed 1-minute estimate does not fail the refresh`() = runTest {
        server.enqueueFixture(SpaceWeatherFixtures.KP_FORECAST)
        server.enqueueFixture(SpaceWeatherFixtures.KP_ONE_MINUTE)
        repository.refreshKp()

        server.enqueueStatus(503)
        val failed = repository.refreshKp()
        assertEquals(503, ((failed as DataResult.Failure).error as AppError.Http).code)
        assertEquals(81, repository.observeKpForecast().first().size)

        server.enqueueFixture(SpaceWeatherFixtures.KP_FORECAST)
        server.enqueueStatus(500)
        assertEquals(DataResult.Success(Unit), repository.refreshKp())
        assertEquals(0.33, repository.observeCurrentKp().first()!!.kp, 0.0)
    }

    @Test
    fun `an answer without a usable slot is an empty response`() = runTest {
        server.enqueueJson("""[{"time_tag":"not a time","kp":3.0,"observed":"observed"}]""")

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
        server.enqueueFixture(SpaceWeatherFixtures.FLARES)
        server.enqueueFixture(SpaceWeatherFixtures.CMES)

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

        assertEquals("/get/FLR?startDate=2026-09-19&endDate=2026-10-19", server.takeRequest().path)
        assertEquals("/get/CME?startDate=2026-10-05&endDate=2026-10-19", server.takeRequest().path)
    }

    @Test
    fun `a quiet Sun is not an error`() = runTest {
        server.enqueueJson("[]")
        server.enqueueJson("[]")

        assertEquals(DataResult.Success(Unit), repository.refreshSolarActivity())
        assertNull(repository.observeLatestFlare().first())
    }

    @Test
    fun `a failed CME call stores nothing`() = runTest {
        server.enqueueFixture(SpaceWeatherFixtures.FLARES)
        server.enqueueJson("""{"status":400,"error":"Bad Request"}""", code = 400)

        assertTrue(repository.refreshSolarActivity() is DataResult.Failure)
        assertNull(repository.observeLatestFlare().first())
    }
}
