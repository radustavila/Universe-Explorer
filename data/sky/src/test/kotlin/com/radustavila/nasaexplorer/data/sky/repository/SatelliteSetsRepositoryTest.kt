package com.radustavila.nasaexplorer.data.sky.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatelliteSet
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/**
 * The sets beyond the ISS, from CelesTrak GP responses recorded on 2026-10-22 (the groups trimmed
 * to a few records), through the real HTTP stack into a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
class SatelliteSetsRepositoryTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-22T13:00:00Z"))
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
    fun `Hubble is asked for by catalogue number and read back as a one-object set`() = runTest {
        server.enqueueFixture(HUBBLE_FIXTURE)

        assertEquals(DataResult.Success(1), repository.refreshSet(SatelliteSet.HUBBLE))

        assertEquals("20580", server.takeRequest().requestUrl!!.queryParameter("CATNR"))
        val hubble = repository.observeSet(SatelliteSet.HUBBLE).first().single()
        assertEquals("HST", hubble.name)
        assertEquals("1990-037B", hubble.internationalDesignator)
        assertEquals(80792L, hubble.revolutionsAtEpoch)
        assertEquals(Instant.parse("2026-10-21T07:25:27.668Z"), hubble.epoch)
    }

    @Test
    fun `the bright set is one group request and keeps every member`() = runTest {
        server.enqueueFixture(VISUAL_FIXTURE)

        assertEquals(DataResult.Success(6), repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES))

        val request = server.takeRequest().requestUrl!!
        assertEquals("visual", request.queryParameter("GROUP"))
        assertEquals("json", request.queryParameter("FORMAT"))
        val members = repository.observeSet(SatelliteSet.BRIGHT_SATELLITES).first()
        assertEquals(listOf(694, 733, 877, 20580, 25544, 48274), members.map { it.noradId })
        // The group's ISS record also serves the ISS card.
        assertNotNull(repository.observeElements(OrbitalElements.ISS_NORAD_ID).first())
    }

    @Test
    fun `the Starlink set keeps only Starlink satellites of the last 30 days`() = runTest {
        server.enqueueFixture(LAST_30_DAYS_FIXTURE)

        assertEquals(DataResult.Success(6), repository.refreshSet(SatelliteSet.STARLINK_TRAINS))

        assertEquals("last-30-days", server.takeRequest().requestUrl!!.queryParameter("GROUP"))
        val starlinks = repository.observeSet(SatelliteSet.STARLINK_TRAINS).first()
        assertTrue(starlinks.all { it.name.startsWith("STARLINK") })
        assertEquals(
            setOf("2026-232A", "2026-232B", "2026-232C", "2026-232D", "2026-225A", "2026-225B"),
            starlinks.mapNotNull { it.internationalDesignator }.toSet(),
        )
    }

    @Test
    fun `a group refresh replaces the members and drops elements no set holds`() = runTest {
        server.enqueueFixture(VISUAL_FIXTURE)
        repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES)
        server.enqueueFixture(VISUAL_SHRUNK_FIXTURE)

        repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES)

        val members = repository.observeSet(SatelliteSet.BRIGHT_SATELLITES).first()
        assertEquals(listOf(694, 25544), members.map { it.noradId })
        // Hubble and Tiangong left the group, but a named object's elements are kept.
        assertEquals(1, repository.observeSet(SatelliteSet.HUBBLE).first().size)
        assertEquals(0, repository.observeElementsCount(733))
    }

    @Test
    fun `a failed group refresh keeps the cached members`() = runTest {
        server.enqueueFixture(VISUAL_FIXTURE)
        repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES)
        server.enqueueStatus(code = 503)

        assertTrue(repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES) is DataResult.Failure)

        assertEquals(6, repository.observeSet(SatelliteSet.BRIGHT_SATELLITES).first().size)
    }

    @Test
    fun `an empty bright group is a failure, an empty Starlink month is not`() = runTest {
        server.enqueueBody("[]")
        server.enqueueBody("[]")

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshSet(SatelliteSet.BRIGHT_SATELLITES))
        assertEquals(DataResult.Success(0), repository.refreshSet(SatelliteSet.STARLINK_TRAINS))
    }

    private suspend fun SatelliteRepositoryImpl.observeElementsCount(noradId: Int): Int =
        if (observeElements(noradId).first() == null) 0 else 1

    private fun FixtureServer.enqueueBody(body: String) {
        enqueue(okhttp3.mockwebserver.MockResponse().setBody(body))
    }

    private companion object {
        const val HUBBLE_FIXTURE = "sky/gp_hubble.json"
        const val VISUAL_FIXTURE = "sky/gp_visual.json"
        const val VISUAL_SHRUNK_FIXTURE = "sky/gp_visual_shrunk.json"
        const val LAST_30_DAYS_FIXTURE = "sky/gp_last_30_days.json"
    }
}
