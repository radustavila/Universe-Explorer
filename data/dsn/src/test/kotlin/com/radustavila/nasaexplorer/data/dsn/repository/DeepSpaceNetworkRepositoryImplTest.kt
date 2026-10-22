package com.radustavila.nasaexplorer.data.dsn.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.DeepSpaceUnits
import com.radustavila.nasaexplorer.core.model.DsnLink
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.data.dsn.local.DsnDatabase
import com.radustavila.nasaexplorer.data.dsn.local.RoomDsnLocalDataSource
import com.radustavila.nasaexplorer.data.dsn.remote.enqueueXml
import com.radustavila.nasaexplorer.data.dsn.remote.remoteDataSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
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

/** The repository end to end: recorded feeds through the real parser into an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class DeepSpaceNetworkRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(
        currentInstant = Instant.parse("2026-10-22T16:13:20Z"),
        currentDate = LocalDate.of(2026, 10, 22),
    )
    private lateinit var database: DsnDatabase
    private lateinit var repository: DeepSpaceNetworkRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DsnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeepSpaceNetworkRepositoryImpl(
            localDataSource = // Not a test dispatcher: Room's flows would run on a scheduler other than runTest's.
            RoomDsnLocalDataSource(database.dsnDao(), Dispatchers.Unconfined),
            remoteDataSource = remoteDataSource(server, clock),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `nothing is stored before the first refresh`() = runTest {
        assertNull(repository.observeSnapshot().first())
        assertTrue(repository.observeFarthestProbes().first().isEmpty())
    }

    @Test
    fun `the recorded feed becomes three sites with idle and active dishes`() = runTest {
        server.enqueueXml(Fixtures.read("dsn/dsn.xml"))
        server.enqueueXml(Fixtures.read("dsn/config.xml"))

        assertEquals(DataResult.Success(Unit), repository.refreshLive())
        assertEquals(DataResult.Success(Unit), repository.refreshConfig())
        val snapshot = repository.observeSnapshot().first()!!

        assertEquals(Instant.ofEpochMilli(1_792_685_598_000L), snapshot.observedAt)
        assertEquals(listOf("Goldstone", "Madrid", "Canberra"), snapshot.sites.map { it.name })
        val goldstone = snapshot.sites[0]
        // DSS14 and DSS23 point at the network's placeholder target: idle, no spacecraft.
        assertEquals(listOf("DSS14", "DSS23"), goldstone.dishes.filter { it.links.isEmpty() }.map { it.name })
        assertEquals(70, goldstone.dishes.first().diameterMeters)

        val voyager = snapshot.sites[2].dishes.single { it.name == "DSS43" }.links.single()
        assertEquals("Voyager 2", voyager.spacecraft.name)
        assertEquals(-32, voyager.spacecraft.naifId)
        assertEquals(21_600_000_000.0, voyager.distanceKm!!, 0.0)
        assertEquals(DsnLink.DistanceSource.DSN, voyager.distanceSource)
        assertEquals(160.0, voyager.downlinkRateBitsPerSecond!!, 0.0)
        // About 20 hours one way.
        assertEquals(20, voyager.oneWayLightTime!!.toHours())

        val mro = goldstone.dishes.single { it.name == "DSS25" }.links.single()
        assertEquals("Mars Reconnaissance Orbiter", mro.spacecraft.name)
        assertNull(mro.distanceKm)
        assertFalse(mro.isActive)
    }

    @Test
    fun `the farthest list is Voyager 1, Voyager 2 and New Horizons, farthest first`() = runTest {
        routeHorizons()

        assertEquals(DataResult.Success(HorizonsTargets.ALL.size), repository.refreshDistances())
        val farthest = repository.observeFarthestProbes().first()

        assertEquals(listOf("Voyager 1", "Voyager 2", "New Horizons"), farthest.map { it.name })
        assertEquals(172.65, farthest.first().distanceAu, 0.01)
    }

    @Test
    fun `a spacecraft without a range in the feed gets today's horizons distance`() = runTest {
        val feed = """
            <dsn>
              <station name="cdscc" friendlyName="Canberra" timeUTC="1792685598000"/>
              <dish name="DSS35" azimuthAngle="100" elevationAngle="30" activity="Telemetry">
                <downSignal active="true" dataRate="2000" band="X" spacecraft="EURC" spacecraftID="-159"/>
                <target name="EURC" id="159" uplegRange="-1" downlegRange="-1" rtlt="-1"/>
              </dish>
              <timestamp>1792685598000</timestamp>
            </dsn>
        """.trimIndent()
        server.route { request ->
            if (request.requestUrl!!.encodedPath.endsWith("dsn.xml")) {
                MockResponse().setHeader("Content-Type", "text/xml").setBody(feed)
            } else {
                horizons(request)
            }
        }
        repository.refreshDistances()

        repository.refreshLive()
        val clipper = repository.observeSnapshot().first()!!.sites.single().dishes.single().links.single()

        assertEquals(DsnLink.DistanceSource.HORIZONS, clipper.distanceSource)
        // 0.311 AU at midnight, closing at 13.4 km/s for the 16 hours since.
        val expected = 0.31122284992684 * DeepSpaceUnits.KM_PER_AU - 13.4057294 * (16 * 3600 + 13 * 60 + 18)
        assertEquals(expected, clipper.distanceKm!!, 1.0)
    }

    @Test
    fun `a failed, empty or broken download keeps the last good state`() = runTest {
        server.enqueueXml(Fixtures.read("dsn/dsn.xml"))
        repository.refreshLive()
        val lastGood = repository.observeSnapshot().first()

        server.enqueueStatus(500)
        server.enqueueXml("<dsn><timestamp>1</timestamp></dsn>")
        server.enqueueXml("<html><body>Down for maintenance</body></html>")

        assertTrue(repository.refreshLive() is DataResult.Failure)
        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshLive())
        // Valid XML in another shape: no dish, so nothing to store.
        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshLive())
        assertEquals(lastGood, repository.observeSnapshot().first())
    }

    @Test
    fun `a new snapshot replaces the old one whole`() = runTest {
        server.enqueueXml(Fixtures.read("dsn/dsn.xml"))
        repository.refreshLive()
        server.enqueueXml(
            """
            <dsn>
              <station name="mdscc" friendlyName="Madrid"/><dish name="DSS63"/><timestamp>1792685603000</timestamp>
            </dsn>
            """.trimIndent(),
        )

        repository.refreshLive()
        val snapshot = repository.observeSnapshot().first()!!

        assertEquals(listOf("Madrid"), snapshot.sites.map { it.name })
        assertEquals(listOf("DSS63"), snapshot.sites.single().dishes.map { it.name })
        assertEquals(0, database.dsnDao().observeLive().first().sumOf { it.signals.size })
    }

    @Test
    fun `distances fail only when no spacecraft could be read, and keep the previous ones`() = runTest {
        routeHorizons()
        repository.refreshDistances()
        val before = repository.observeFarthestProbes().first()

        server.route { MockResponse().setResponseCode(503) }
        val result = repository.refreshDistances()

        assertEquals(DataResult.Failure(AppError.Http(503, "Server Error")), result)
        assertEquals(before, repository.observeFarthestProbes().first())
    }

    @Test
    fun `a spacecraft horizons does not answer for is skipped`() = runTest {
        server.route { request ->
            when (request.requestUrl!!.queryParameter("COMMAND")) {
                "'-31'" -> json("dsn/horizons_31.json")
                else -> json("dsn/horizons_9999.json")
            }
        }

        assertEquals(DataResult.Success(1), repository.refreshDistances())
        assertEquals(listOf("Voyager 1"), repository.observeFarthestProbes().first().map { it.name })
    }

    private fun routeHorizons() = server.route(::horizons)

    private fun horizons(request: RecordedRequest): MockResponse {
        val id = request.requestUrl!!.queryParameter("COMMAND")!!.trim('\'').removePrefix("-")
        return json("dsn/horizons_$id.json")
    }

    private fun json(fixture: String) =
        MockResponse().setHeader("Content-Type", "application/json").setBody(Fixtures.read(fixture))
}
