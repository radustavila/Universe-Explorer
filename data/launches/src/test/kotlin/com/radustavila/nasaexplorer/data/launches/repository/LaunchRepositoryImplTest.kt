package com.radustavila.nasaexplorer.data.launches.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.launches.local.RoomLaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.remote.RetrofitLaunchRemoteDataSource
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.data.launches.remote.PREVIOUS_DETAILED_FIXTURE
import com.radustavila.nasaexplorer.data.launches.remote.SCRUBBED_FIXTURE
import com.radustavila.nasaexplorer.data.launches.remote.UPCOMING_DETAILED_FIXTURE
import com.radustavila.nasaexplorer.data.launches.remote.UPCOMING_FIXTURE
import com.radustavila.nasaexplorer.data.launches.remote.launchLibraryService
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

/**
 * The launches pipeline end to end - recorded LL2 response, real HTTP stack, real Room: the cache is
 * replaced on success and kept on every kind of failure.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var database: LaunchesDatabase
    private lateinit var repository: LaunchRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaunchesDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = LaunchRepositoryImpl(
            localDataSource = RoomLaunchLocalDataSource(database.launchDao(), io),
            remoteDataSource = RetrofitLaunchRemoteDataSource(launchLibraryService(server), io),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a refresh caches the upcoming launches soonest first in one call`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)

        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Success(5), result)
        assertEquals(1, server.requestCount)
        val cached = repository.observeUpcomingLaunches().first()
        assertEquals(5, cached.size)
        assertEquals(cached.sortedBy { it.net }, cached)
    }

    @Test
    fun `a 429 surfaces as RateLimited and keeps the cached launches`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)
        repository.refreshUpcomingLaunches()
        val cached = repository.observeUpcomingLaunches().first()

        server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "5400"))
        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 5400)), result)
        assertEquals(cached, repository.observeUpcomingLaunches().first())
    }

    @Test
    fun `a server error keeps the cached launches`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)
        repository.refreshUpcomingLaunches()
        val cached = repository.observeUpcomingLaunches().first()

        server.enqueueStatus(code = 503)
        val result = repository.refreshUpcomingLaunches()

        assertTrue((result as DataResult.Failure).error is AppError.Http)
        assertEquals(cached, repository.observeUpcomingLaunches().first())
    }

    @Test
    fun `a response with nothing usable keeps the cached launches`() = runTest {
        server.enqueueFixture(UPCOMING_FIXTURE)
        repository.refreshUpcomingLaunches()
        val cached = repository.observeUpcomingLaunches().first()

        server.enqueueJson("""{"results": [{"id": "x"}]}""")
        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), result)
        assertEquals(cached, repository.observeUpcomingLaunches().first())
    }

    @Test
    fun `observers see the refreshed launches replace the old ones`() = runTest {
        repository.observeUpcomingLaunches().test {
            assertTrue(awaitItem().isEmpty())

            server.enqueueFixture(UPCOMING_FIXTURE)
            repository.refreshUpcomingLaunches()

            assertEquals(5, awaitItem().size)
        }
    }

    @Test
    fun `past launches live beside the upcoming ones, most recent first`() = runTest {
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        server.enqueueFixture(PREVIOUS_DETAILED_FIXTURE)

        assertEquals(DataResult.Success(4), repository.refreshUpcomingLaunches())
        assertEquals(DataResult.Success(4), repository.refreshPastLaunches())

        val upcoming = repository.observeUpcomingLaunches().first()
        val past = repository.observePastLaunches().first()
        assertEquals(4, upcoming.size)
        assertTrue(upcoming.all { it.timeline == LaunchTimeline.UPCOMING })
        assertEquals(past.sortedByDescending { it.net }, past)
        assertTrue(past.all { it.timeline == LaunchTimeline.PAST })
        assertEquals("Falcon 9 Block 5 | Crew-13", repository.observeLaunch(CREW_13).first()?.name)
    }

    @Test
    fun `refreshing one list leaves the other alone`() = runTest {
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        server.enqueueFixture(PREVIOUS_DETAILED_FIXTURE)
        repository.refreshUpcomingLaunches()
        repository.refreshPastLaunches()

        server.enqueueJson("""{"results": []}""")
        repository.refreshPastLaunches()

        assertEquals(4, repository.observeUpcomingLaunches().first().size)
        assertTrue(repository.observePastLaunches().first().isEmpty())
    }

    @Test
    fun `a launch that flew moves from the upcoming list to the past one`() = runTest {
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        repository.refreshUpcomingLaunches()
        // The previous list now has these launches (as it does once they flew).
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        repository.refreshPastLaunches()

        assertTrue(repository.observeUpcomingLaunches().first().isEmpty())
        assertEquals(LaunchTimeline.PAST, repository.observeLaunch(STARLINK).first()?.timeline)
    }

    @Test
    fun `a re-check updates one launch in its list with one call`() = runTest {
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        repository.refreshUpcomingLaunches()
        server.enqueueFixture(SCRUBBED_FIXTURE)

        val result = repository.refreshLaunch(STARLINK)

        val scrubbed = (result as DataResult.Success).data
        assertEquals(LaunchStatus.HOLD, scrubbed.status)
        assertEquals(LaunchTimeline.UPCOMING, scrubbed.timeline)
        assertEquals(scrubbed, repository.observeLaunch(STARLINK).first())
        assertEquals(4, repository.observeUpcomingLaunches().first().size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed re-check keeps the cached launch`() = runTest {
        server.enqueueFixture(UPCOMING_DETAILED_FIXTURE)
        repository.refreshUpcomingLaunches()
        val cached = repository.observeLaunch(STARLINK).first()

        server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "600"))
        val result = repository.refreshLaunch(STARLINK)

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 600)), result)
        assertEquals(cached, repository.observeLaunch(STARLINK).first())
        assertNull(repository.observeLaunch("not-cached").first())
    }

    private companion object {
        const val STARLINK = "e2886a96-5ce9-4f45-872f-4e25a00be837"
        const val CREW_13 = "18441371-8b2e-457c-afb5-1ec1b11ab630"
    }
}
