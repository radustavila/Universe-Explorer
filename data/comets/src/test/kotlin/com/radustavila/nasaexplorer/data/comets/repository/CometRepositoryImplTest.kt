package com.radustavila.nasaexplorer.data.comets.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.comets.local.CometsDatabase
import com.radustavila.nasaexplorer.data.comets.local.RoomCometsLocalDataSource
import com.radustavila.nasaexplorer.data.comets.remote.remoteDataSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

/** The repository end to end: recorded SBDB and Horizons answers through the parsers into an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class CometRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(
        currentInstant = Instant.parse("2026-10-23T00:00:00Z"),
        currentDate = LocalDate.of(2026, 10, 23),
    )
    private lateinit var database: CometsDatabase
    private lateinit var repository: CometRepositoryImpl

    /** Designations Horizons answers with an error for, as if the request had failed. */
    private val failingHorizons = mutableSetOf<String>()
    private var sbdbFixture = "comets/sbdb_comets.json"
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CometsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CometRepositoryImpl(
            // Not a test dispatcher: Room's flows would run on a scheduler other than runTest's.
            localDataSource = RoomCometsLocalDataSource(database.cometsDao(), Dispatchers.Unconfined),
            remoteDataSource = remoteDataSource(server),
            timeProvider = clock,
        )
        server.route(::respond)
    }

    @After
    fun tearDown() = database.close()

    private fun respond(request: RecordedRequest): MockResponse {
        val url = request.requestUrl!!
        requests += url.encodedPath
        if (url.encodedPath == "/sbdb_query.api") return server.fixtureResponse(sbdbFixture)
        val designation = url.queryParameter("COMMAND")!!.removePrefix("'DES=").substringBefore(';')
        return if (designation in failingHorizons) {
            MockResponse().setResponseCode(503)
        } else {
            server.fixtureResponse("comets/horizons_${designation.replace(' ', '_')}.json")
        }
    }

    @Test
    fun `nothing is cached before the first refresh`() = runTest {
        assertTrue(repository.observeComets().first().isEmpty())
        assertNull(repository.observeComet("2026 A2").first())
    }

    @Test
    fun `a refresh caches every candidate with its 121 days`() = runTest {
        assertEquals(DataResult.Success(5), repository.refresh())

        val comets = repository.observeComets().first()
        assertEquals(5, comets.size)
        comets.forEach { assertEquals(it.designation, 121, it.ephemeris.size) }
        val bok = repository.observeComet("2026 A2").first()!!
        assertEquals("C/2026 A2 (Bok)", bok.name)
        assertEquals(Instant.parse("2026-12-22T08:24:00Z"), bok.perihelionTime)
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), bok.ephemeris.first().time)
        assertEquals(14.152, bok.ephemeris.single { it.time == Instant.parse("2026-10-23T00:00:00Z") }.magnitude!!, 0.0)
        // One SBDB query, then one Horizons request per comet, brightest absolute magnitude first.
        assertEquals(listOf("/sbdb_query.api") + List(5) { "/api/horizons.api" }, requests)
    }

    @Test
    fun `a comet whose ephemeris fails keeps the one it had`() = runTest {
        repository.refresh()
        failingHorizons += "2026 O1"
        clock.currentInstant = Instant.parse("2026-10-24T00:00:00Z")

        assertEquals(DataResult.Success(5), repository.refresh())

        assertEquals(121, repository.observeComet("2026 O1").first()!!.ephemeris.size)
    }

    @Test
    fun `when every ephemeris fails the cache is left as it was`() = runTest {
        failingHorizons += listOf("2018 VN2", "2025 V2", "2026 A2", "2026 AZ17", "2026 O1")

        val result = repository.refresh()

        assertEquals(AppError.Http(503, "Server Error"), (result as DataResult.Failure).error)
        assertTrue(repository.observeComets().first().isEmpty())
    }

    @Test
    fun `comets SBDB no longer lists are dropped with their days`() = runTest {
        repository.refresh()
        sbdbFixture = "comets/sbdb_empty.json"

        assertEquals(DataResult.Success(0), repository.refresh())

        assertTrue(repository.observeComets().first().isEmpty())
        assertNull(database.cometsDao().newestEphemerisFetchedAt())
    }
}
