package com.radustavila.nasaexplorer.data.launches.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRefreshOutcome
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.data.launches.LaunchFixtures
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.launches.local.RoomLaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.RoomLaunchRefreshThrottle
import com.radustavila.nasaexplorer.data.launches.remote.RetrofitLaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.remote.launchLibraryService
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The whole launches pipeline - recorded LL2 response, real HTTP stack, real Room - with the clock
 * as the only fake, because the refresh budget is what this repository exists to protect.
 */
@RunWith(RobolectricTestRunner::class)
class LaunchRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var database: LaunchesDatabase
    private lateinit var repository: LaunchRepositoryImpl

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-19T10:00:00Z"))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LaunchesDatabase::class.java,
        ).allowMainThreadQueries().build()

        val io = UnconfinedTestDispatcher()
        repository = LaunchRepositoryImpl(
            localDataSource = RoomLaunchLocalDataSource(database.launchDao(), io),
            remoteDataSource = RetrofitLaunchRemoteDataSource(launchLibraryService(server), io),
            throttle = RoomLaunchRefreshThrottle(database.launchDao(), clock, io),
            timeProvider = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `a refresh caches the upcoming launches soonest first`() = runTest {
        server.enqueue(fixture())

        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Success(LaunchRefreshOutcome.REFRESHED), result)
        val cached = repository.observeUpcomingLaunches().first()
        assertEquals(5, cached.size)
        assertEquals(cached.sortedBy { it.net }, cached)
    }

    @Test
    fun `a second refresh inside the hour makes no network call`() = runTest {
        server.enqueue(fixture())
        repository.refreshUpcomingLaunches()
        clock.advanceBy(Duration.ofMinutes(59))

        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Success(LaunchRefreshOutcome.SKIPPED), result)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `refreshes again once the hour has passed`() = runTest {
        server.enqueue(fixture())
        server.enqueue(fixture())
        repository.refreshUpcomingLaunches()
        clock.advanceBy(Duration.ofMinutes(60))

        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Success(LaunchRefreshOutcome.REFRESHED), result)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed call still counts against the budget`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        repository.refreshUpcomingLaunches()
        clock.advanceBy(Duration.ofMinutes(30))

        assertEquals(DataResult.Success(LaunchRefreshOutcome.SKIPPED), repository.refreshUpcomingLaunches())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 429 surfaces as RateLimited, keeps the cache and waits out Retry-After`() = runTest {
        server.enqueue(fixture())
        repository.refreshUpcomingLaunches()
        val cached = repository.observeUpcomingLaunches().first()

        clock.advanceBy(Duration.ofMinutes(60))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5400"))
        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 5400)), result)
        assertEquals(cached, repository.observeUpcomingLaunches().first())

        // Past the usual hour but inside Retry-After: still no call.
        clock.advanceBy(Duration.ofMinutes(61))
        assertEquals(DataResult.Success(LaunchRefreshOutcome.SKIPPED), repository.refreshUpcomingLaunches())
        clock.advanceBy(Duration.ofMinutes(30))
        server.enqueue(fixture())
        assertEquals(DataResult.Success(LaunchRefreshOutcome.REFRESHED), repository.refreshUpcomingLaunches())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a response with nothing usable keeps the cached launches`() = runTest {
        server.enqueue(fixture())
        repository.refreshUpcomingLaunches()
        val cached = repository.observeUpcomingLaunches().first()

        clock.advanceBy(Duration.ofMinutes(60))
        server.enqueue(MockResponse().setBody("""{"results": [{"id": "x"}]}"""))
        val result = repository.refreshUpcomingLaunches()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), result)
        assertEquals(cached, repository.observeUpcomingLaunches().first())
    }

    @Test
    fun `observers see the refreshed launches replace the old ones`() = runTest {
        repository.observeUpcomingLaunches().test {
            assertTrue(awaitItem().isEmpty())

            server.enqueue(fixture())
            repository.refreshUpcomingLaunches()

            assertEquals(5, awaitItem().size)
        }
    }

    @Test
    fun `a clock set backwards does not block refreshes for days`() = runTest {
        server.enqueue(fixture())
        server.enqueue(fixture())
        repository.refreshUpcomingLaunches()
        clock.currentInstant = clock.currentInstant.minus(Duration.ofDays(2))

        assertEquals(DataResult.Success(LaunchRefreshOutcome.REFRESHED), repository.refreshUpcomingLaunches())
    }

    private fun fixture() = MockResponse().setBody(LaunchFixtures.UPCOMING_DETAILED)

    private fun FakeTimeProvider.advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
