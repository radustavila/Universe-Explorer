package com.radustavila.nasaexplorer.data.climate.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimatePoint
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.climate.local.ClimateDatabase
import com.radustavila.nasaexplorer.data.climate.local.RoomClimateLocalDataSource
import com.radustavila.nasaexplorer.data.climate.remote.ClimateSourceUrls
import com.radustavila.nasaexplorer.data.climate.remote.RetrofitClimateRemoteDataSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/** The repository end to end: the recorded files through the parsers into an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class ClimateRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-23T09:00:00Z"))
    private lateinit var database: ClimateDatabase
    private lateinit var repository: ClimateRepositoryImpl

    /** Files answered with a server error. */
    private val failing = mutableSetOf<String>()

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ClimateDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val retrofit = RetrofitFactory(Json, OkHttpClient()).create(server.baseUrl)
        repository = ClimateRepositoryImpl(
            // Not a test dispatcher: Room's flows would run on a scheduler other than runTest's.
            localDataSource = RoomClimateLocalDataSource(database.climateDao(), Dispatchers.Unconfined),
            remoteDataSource = RetrofitClimateRemoteDataSource(
                service = retrofit.create(),
                urls = ClimateSourceUrls(FILES.mapValues { (_, file) -> "${server.baseUrl}files/$file".toHttpUrl() }),
                ioDispatcher = UnconfinedTestDispatcher(),
            ),
            timeProvider = clock,
        )
        server.route(::respond)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun respond(request: RecordedRequest): MockResponse {
        val file = checkNotNull(request.requestUrl).pathSegments.last()
        if (file in failing) return MockResponse().setResponseCode(HTTP_UNAVAILABLE)
        return server.fixtureResponse("climate/$file")
    }

    @Test
    fun `each indicator refreshes from its own file and is observed in indicator order`() = runTest {
        ClimateIndicator.entries.reversed().forEach { indicator ->
            assertTrue(repository.refresh(indicator) is DataResult.Success)
        }

        val series = repository.observeSeries().first()
        assertEquals(ClimateIndicator.entries, series.map { it.indicator })
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 426.07), series[0].latest)
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 1.38), series[1].latest)
        assertEquals(LocalDate.of(2025, 2, 16), series[2].latest?.date)
        assertEquals(ClimatePoint(LocalDate.of(2026, 9, 1), 4.81), series[3].latest)
    }

    @Test
    fun `a refresh replaces the indicator's values and returns their count`() = runTest {
        val first = repository.refresh(ClimateIndicator.ARCTIC_SEA_ICE)
        val second = repository.refresh(ClimateIndicator.ARCTIC_SEA_ICE)

        assertEquals(DataResult.Success(48), first)
        assertEquals(DataResult.Success(48), second)
        assertEquals(48, repository.observeSeries().first().single().points.size)
    }

    @Test
    fun `a failed refresh keeps the last good values`() = runTest {
        repository.refresh(ClimateIndicator.CO2)
        failing += FILES.getValue(ClimateIndicator.CO2)

        val result = repository.refresh(ClimateIndicator.CO2)

        assertTrue((result as DataResult.Failure).error is AppError.Http)
        assertEquals(426.07, repository.observeSeries().first().single().latest?.value)
    }

    @Test
    fun `a page that is not the file is a format failure`() = runTest {
        server.route { MockResponse().setBody("<html>Moved</html>") }

        val result = repository.refresh(ClimateIndicator.GLOBAL_TEMPERATURE)

        assertTrue((result as DataResult.Failure).error is AppError.Serialization)
        assertEquals(emptyList<Any>(), repository.observeSeries().first())
    }

    private companion object {
        const val HTTP_UNAVAILABLE = 503
        val FILES = mapOf(
            ClimateIndicator.CO2 to "co2_mm_mlo.csv",
            ClimateIndicator.GLOBAL_TEMPERATURE to "GLB.Ts+dSST.csv",
            ClimateIndicator.SEA_LEVEL to "slr_sla_gbl_free_ref_90.csv",
            ClimateIndicator.ARCTIC_SEA_ICE to "N_09_extent_v4.0.csv",
        )
    }
}
