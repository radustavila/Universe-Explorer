package com.radustavila.nasaexplorer.data.sky.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.sky.local.SkyDatabase
import com.radustavila.nasaexplorer.data.sky.remote.RetrofitSkyRemoteDataSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/**
 * Recorded CelesTrak GP and wheretheiss.at responses (2026-10-19) through the real HTTP stack into
 * a real Room database. Both services are served by one fixture server, in request order.
 */
@RunWith(RobolectricTestRunner::class)
class SatelliteRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T13:00:00Z"))
    private lateinit var database: SkyDatabase
    private lateinit var repository: SatelliteRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val retrofit = RetrofitFactory(
            json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true },
            callFactory = OkHttpClient(),
        ).create(server.baseUrl)
        val io = UnconfinedTestDispatcher()
        repository = SatelliteRepositoryImpl(
            dao = database.orbitalElementsDao(),
            remoteDataSource = RetrofitSkyRemoteDataSource(retrofit.create(), retrofit.create(), io),
            timeProvider = clock,
            ioDispatcher = io,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh caches the ISS elements from one CelesTrak call`() = runTest {
        server.enqueueFixture(GP_FIXTURE)

        assertEquals(DataResult.Success(Unit), repository.refreshElements())

        val request = server.takeRequest().requestUrl!!
        assertEquals("/NORAD/elements/gp.php", request.encodedPath)
        assertEquals("25544", request.queryParameter("CATNR"))
        assertEquals("json", request.queryParameter("FORMAT"))
        val elements = repository.observeElements(OrbitalElements.ISS_NORAD_ID).first()!!
        assertEquals("ISS (ZARYA)", elements.name)
        // OMM epochs carry no zone; they are UTC.
        assertEquals(Instant.parse("2026-10-19T04:14:45.011Z"), elements.epoch)
        assertEquals(15.48934931, elements.meanMotion, 0.0)
        assertEquals(0.00067631, elements.eccentricity, 0.0)
        assertEquals(51.6325, elements.inclinationDegrees, 0.0)
        assertEquals(0.00013772264, elements.bstar, 0.0)
    }

    @Test
    fun `a failed refresh keeps the cached elements`() = runTest {
        server.enqueueFixture(GP_FIXTURE)
        repository.refreshElements()

        server.enqueueStatus(code = 503)

        assertEquals(DataResult.Failure(AppError.Http(503)), repository.refreshElements().stripMessage())
        assertTrue(repository.observeElements(OrbitalElements.ISS_NORAD_ID).first() != null)
    }

    @Test
    fun `CelesTrak's plain-text answer for an unknown object is a serialization failure`() = runTest {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("No GP data found"))

        assertTrue((repository.refreshElements() as DataResult.Failure).error is AppError.Serialization)
        assertNull(repository.observeElements(OrbitalElements.ISS_NORAD_ID).first())
    }

    @Test
    fun `the live position is mapped and never cached`() = runTest {
        server.enqueueFixture(POSITION_FIXTURE)

        val position = (repository.fetchLivePosition(OrbitalElements.ISS_NORAD_ID) as DataResult.Success).data

        assertEquals("/v1/satellites/25544", server.takeRequest().requestUrl!!.encodedPath)
        assertEquals(GeoCoordinate(35.720881650186, -155.14078697917), position.coordinate)
        assertEquals(424.98, position.altitudeKm, 0.01)
        assertEquals(27_574.8, position.speedKmPerHour, 0.1)
        assertEquals(false, position.isSunlit)
        assertEquals(Instant.ofEpochSecond(1_792_417_472), position.at)
        assertTrue(position.isLive)
        assertNull(repository.observeElements(OrbitalElements.ISS_NORAD_ID).first())
    }

    private fun DataResult<Unit>.stripMessage(): DataResult<Unit> = when (this) {
        is DataResult.Failure -> (error as? AppError.Http)?.let { DataResult.Failure(it.copy(message = null)) } ?: this
        is DataResult.Success -> this
    }

    private companion object {
        const val GP_FIXTURE = "sky/gp_iss.json"
        const val POSITION_FIXTURE = "sky/wheretheiss_iss.json"
    }
}
