package com.radustavila.nasaexplorer.data.smallbodies.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.smallbodies.SmallBodiesFixtures
import com.radustavila.nasaexplorer.data.smallbodies.jplSsdService
import com.radustavila.nasaexplorer.data.smallbodies.local.RoomSmallBodiesLocalDataSource
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDatabase
import com.radustavila.nasaexplorer.data.smallbodies.remote.RetrofitSmallBodiesRemoteDataSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Recorded JPL answers through the real HTTP stack into real Room. */
@RunWith(RobolectricTestRunner::class)
class SmallBodiesRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T14:20:00Z"))
    private lateinit var database: SmallBodiesDatabase
    private lateinit var repository: SmallBodiesRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmallBodiesDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = SmallBodiesRepositoryImpl(
            local = RoomSmallBodiesLocalDataSource(database.smallBodiesDao(), io),
            remote = RetrofitSmallBodiesRemoteDataSource(jplSsdService(server), io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    private fun enqueueRefresh() {
        server.enqueueFixture(SmallBodiesFixtures.CAD)
        server.enqueueFixture(SmallBodiesFixtures.SENTRY)
        server.enqueueFixture(SmallBodiesFixtures.FIREBALLS)
    }

    @Test
    fun `a refresh caches approaches with their Sentry badge, and fireballs`() = runTest {
        enqueueRefresh()

        assertEquals(DataResult.Success(Unit), repository.refresh())

        val approaches = repository.observeCloseApproaches().first()
        assertEquals(30, approaches.size)
        assertEquals(approaches.sortedBy { it.time }, approaches)
        val risky = approaches.single { it.sentry != null }
        assertEquals("2025 UK9", risky.designation)
        assertEquals(1.593373699e-4, risky.sentry!!.impactProbability, 1e-12)
        assertEquals(-6.68, risky.sentry!!.palermoMax!!, 0.0)
        assertEquals(89, risky.sentry!!.potentialImpacts)
        assertEquals("2040-2125", risky.sentry!!.yearRange)

        val fireballs = repository.observeRecentFireballs().first()
        assertEquals(20, fireballs.size)
        assertEquals(Instant.parse("2026-10-04T03:14:45Z"), fireballs.first().time)

        val cad = server.takeRequest().path!!
        assertTrue(cad, cad.startsWith("/cad.api?"))
        assertTrue(cad, "date-max=%2B30" in cad)
        assertTrue(cad, "dist-max=0.05" in cad && "diameter=true" in cad && "fullname=true" in cad)
        assertEquals("/sentry.api", server.takeRequest().path)
        assertEquals("/fireball.api?limit=20", server.takeRequest().path)
    }

    @Test
    fun `a failed call leaves yesterday's lists in place`() = runTest {
        enqueueRefresh()
        repository.refresh()

        server.enqueueFixture(SmallBodiesFixtures.CAD)
        server.enqueueStatus(500)

        assertTrue(repository.refresh() is DataResult.Failure)
        assertEquals(30, repository.observeCloseApproaches().first().size)
        assertEquals(20, repository.observeRecentFireballs().first().size)
    }

    @Test
    fun `an empty Sentry list is treated as a broken answer`() = runTest {
        server.enqueueFixture(SmallBodiesFixtures.CAD)
        server.enqueueJson("""{"signature":{"version":"2.0"},"count":"0","data":[]}""")

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refresh())
    }

    @Test
    fun `details are looked up once and cached under the asked designation`() = runTest {
        server.enqueueFixture(SmallBodiesFixtures.SBDB_2020_FN3)

        val details = (repository.fetchDetails("2020 FN3") as DataResult.Success).data

        assertEquals("Apollo", details.orbitClass)
        assertTrue(details.isNearEarthObject)
        assertFalse(details.isPotentiallyHazardous)
        assertEquals(0.00204, details.earthMoidAu!!, 0.0)
        assertEquals(372.0, details.orbitalPeriodDays!!, 0.0)
        assertEquals(66, details.observationsUsed)
        assertEquals(details, repository.observeDetails("2020 FN3").first())
        assertEquals("/sbdb.api?sstr=2020%20FN3&phys-par=true", server.takeRequest().path)
    }

    @Test
    fun `physical parameters fill size and brightness when JPL has them`() = runTest {
        server.enqueueFixture(SmallBodiesFixtures.SBDB_APOPHIS)

        val details = (repository.fetchDetails("99942") as DataResult.Success).data

        assertEquals("99942 Apophis (2004 MN4)", details.name)
        assertTrue(details.isPotentiallyHazardous)
        assertEquals(0.34, details.diameterKm!!, 0.0)
        assertEquals(19.09, details.absoluteMagnitude!!, 0.0)
    }

    @Test
    fun `an unknown object is an empty response and caches nothing`() = runTest {
        server.enqueueFixture(SmallBodiesFixtures.SBDB_NOT_FOUND)

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.fetchDetails("zzzz"))
        assertNull(repository.observeDetails("zzzz").first())
    }
}
