package com.radustavila.nasaexplorer.data.exoplanets.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.DiscoveryMethod
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetsDatabase
import com.radustavila.nasaexplorer.data.exoplanets.local.RoomExoplanetLocalDataSource
import com.radustavila.nasaexplorer.data.exoplanets.remote.CATALOG_FIXTURE
import com.radustavila.nasaexplorer.data.exoplanets.remote.COUNT_FIXTURE
import com.radustavila.nasaexplorer.data.exoplanets.remote.ExoplanetQueries
import com.radustavila.nasaexplorer.data.exoplanets.remote.RetrofitExoplanetRemoteDataSource
import com.radustavila.nasaexplorer.data.exoplanets.remote.exoplanetArchiveService
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

/**
 * The exoplanet pipeline end to end - recorded TAP responses, real HTTP stack, real Room: one
 * refresh feeds every list, and a failure keeps what was cached.
 */
@RunWith(RobolectricTestRunner::class)
class ExoplanetRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentDate = LocalDate.of(2026, 10, 19))
    private lateinit var database: ExoplanetsDatabase
    private lateinit var repository: ExoplanetRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExoplanetsDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = ExoplanetRepositoryImpl(
            localDataSource = RoomExoplanetLocalDataSource(database.exoplanetDao(), io),
            remoteDataSource = RetrofitExoplanetRemoteDataSource(exoplanetArchiveService(server), io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    /** The catalogue and the count are asked at once, so they are answered by query, not in order. */
    private fun enqueueRefresh(
        catalog: MockResponse = server.fixtureResponse(CATALOG_FIXTURE),
        count: MockResponse = server.fixtureResponse(COUNT_FIXTURE),
    ) {
        server.route { request ->
            if (request.requestUrl?.queryParameter("query") == ExoplanetQueries.CONFIRMED_COUNT) count else catalog
        }
    }

    @Test
    fun `a refresh asks for this year and last, and stores every planet and the total`() = runTest {
        enqueueRefresh()

        val result = repository.refresh()

        assertEquals(DataResult.Success(24), result)
        val queries = List(2) { server.takeRequest().requestUrl!!.queryParameter("query") }.toSet()
        assertEquals(setOf(ExoplanetQueries.catalog(2025), ExoplanetQueries.CONFIRMED_COUNT), queries)
        assertEquals(ExoplanetCatalogStats(confirmedPlanets = 6451), repository.observeCatalogStats().first())
    }

    @Test
    fun `newest lists recent discoveries by publication month, newest first`() = runTest {
        enqueueRefresh()
        repository.refresh()

        val newest = repository.observeList(ExoplanetList.NEWEST).first()

        assertEquals(listOf("TIC 219893931 b", "TIC 237254473 b"), newest.take(2).map { it.name })
        assertTrue(newest.all { it.discoveryYear >= 2025 })
        assertEquals(newest.sortedByDescending { it.discoveryPublished }, newest)
        // Barnard b (2024) is cached for the other lists but is not "newest".
        assertTrue(newest.none { it.name == "Barnard b" })
    }

    @Test
    fun `nearest lists systems by distance`() = runTest {
        enqueueRefresh()
        repository.refresh()

        val nearest = repository.observeList(ExoplanetList.NEAREST).first()

        assertEquals(
            listOf("Proxima Cen b", "Proxima Cen d", "Barnard b", "Barnard c"),
            nearest.take(4).map { it.name },
        )
        assertTrue(nearest.none { it.distanceParsecs == null })
    }

    @Test
    fun `earth-sized lists planets of 0_8 to 1_25 earth radii, unknown distance last`() = runTest {
        enqueueRefresh()
        repository.refresh()

        val earthSized = repository.observeList(ExoplanetList.EARTH_SIZED).first()

        assertEquals("Proxima Cen b", earthSized.first().name)
        assertEquals("G 268-110 b", earthSized.last().name)
        assertTrue(earthSized.all { it.radiusEarth!! in 0.8..1.25 })
        assertEquals(9, earthSized.size)
    }

    @Test
    fun `a cached planet is observable by name`() = runTest {
        enqueueRefresh()
        repository.refresh()

        val planet = repository.observeExoplanet("KMT-2023-BLG-0084L b").first()!!

        assertEquals(DiscoveryMethod.MICROLENSING, planet.discoveryMethod)
        assertEquals(13046.3, planet.distanceLightYears!!, 0.1)
        assertNull(repository.observeExoplanet("Vulcan").first())
    }

    @Test
    fun `a failed download keeps the cached catalogue`() = runTest {
        enqueueRefresh()
        repository.refresh()
        val cached = repository.observeList(ExoplanetList.NEWEST).first()

        enqueueRefresh(catalog = MockResponse().setResponseCode(503))
        val result = repository.refresh()

        assertEquals(DataResult.Failure(AppError.Http(code = 503, message = "Server Error")), result)
        assertEquals(cached, repository.observeList(ExoplanetList.NEWEST).first())
    }

    @Test
    fun `a catalogue with nothing usable is an empty response and stores nothing`() = runTest {
        enqueueRefresh(
            catalog = MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""[{"pl_name": null, "hostname": "X", "disc_year": 2026}]"""),
        )

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refresh())
        assertTrue(repository.observeList(ExoplanetList.NEAREST).first().isEmpty())
    }

    @Test
    fun `a failed count still stores the planets and keeps the previous total`() = runTest {
        enqueueRefresh()
        repository.refresh()

        enqueueRefresh(count = MockResponse().setResponseCode(500))
        val result = repository.refresh()

        assertEquals(DataResult.Success(24), result)
        assertEquals(6451, repository.observeCatalogStats().first()?.confirmedPlanets)
    }
}
