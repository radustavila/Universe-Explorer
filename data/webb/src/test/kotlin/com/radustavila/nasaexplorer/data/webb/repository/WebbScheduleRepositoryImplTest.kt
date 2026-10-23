package com.radustavila.nasaexplorer.data.webb.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.webb.local.RoomWebbLocalDataSource
import com.radustavila.nasaexplorer.data.webb.local.WebbDatabase
import com.radustavila.nasaexplorer.data.webb.remote.RetrofitWebbRemoteDataSource
import com.radustavila.nasaexplorer.data.webb.remote.WebbScheduleIndexUrl
import com.radustavila.nasaexplorer.data.webb.remote.WebbScheduleParser
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

/** The repository end to end: the recorded index page and weekly files through the parsers into an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
class WebbScheduleRepositoryImplTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider(
        currentInstant = Instant.parse("2026-10-23T09:00:00Z"),
        currentDate = LocalDate.of(2026, 10, 23),
    )
    private lateinit var database: WebbDatabase
    private lateinit var repository: WebbScheduleRepositoryImpl

    /** Paths answered with a server error, as if STScI were down. */
    private val failing = mutableSetOf<String>()
    private val requests = mutableListOf<String>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WebbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val retrofit = RetrofitFactory(Json, OkHttpClient()).create(server.baseUrl)
        repository = WebbScheduleRepositoryImpl(
            // Not a test dispatcher: Room's flows would run on a scheduler other than runTest's.
            localDataSource = RoomWebbLocalDataSource(database.webbDao(), Dispatchers.Unconfined),
            remoteDataSource = RetrofitWebbRemoteDataSource(
                service = retrofit.create(),
                indexUrl = WebbScheduleIndexUrl("${server.baseUrl}$INDEX_PATH".toHttpUrl()),
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
        val path = checkNotNull(request.requestUrl).encodedPath
        requests += path.substringAfterLast('/')
        val fixture = when {
            path.endsWith(INDEX_PATH) -> "webb/observing_schedules.html"
            else -> "webb/${path.substringAfterLast('/')}"
        }
        return if (path.substringAfterLast('/') in failing) {
            MockResponse().setResponseCode(HTTP_UNAVAILABLE)
        } else {
            server.fixtureResponse(fixture)
        }
    }

    @Test
    fun `a refresh stores the newest week from the index`() = runTest {
        val result = repository.refresh()

        assertEquals(DataResult.Success(44), result)
        assertEquals(listOf("observing-schedules", NEWEST), requests)
        val schedule = checkNotNull(repository.observeSchedule().first())
        assertEquals(44, schedule.visits.size)
        assertEquals(clock.currentInstant, schedule.fetchedAt)
        val first = schedule.visits.first()
        assertEquals("7782:16:1", first.visitId)
        assertEquals(listOf("NIRCam Imaging"), first.parallels)
        assertEquals(schedule.visits.sortedBy { it.start }, schedule.visits)
    }

    @Test
    fun `while the newest week is ahead the week before fills the days until it starts`() = runTest {
        clock.currentInstant = Instant.parse("2026-10-18T12:00:00Z")

        val result = repository.refresh()

        val newestStart = Instant.parse("2026-10-19T00:57:55Z")
        val before = WebbScheduleParser.parse(Fixtures.read("webb/$WEEK_BEFORE")).filter { it.start < newestStart }
        assertEquals(DataResult.Success(44 + before.size), result)
        assertEquals(listOf("observing-schedules", NEWEST, WEEK_BEFORE), requests)
        val visits = checkNotNull(repository.observeSchedule().first()).visits
        assertEquals("6793:1:1", visits.first().visitId)
        assertEquals(newestStart, visits[before.size].start)
    }

    @Test
    fun `a failed week before still stores the newest week`() = runTest {
        clock.currentInstant = Instant.parse("2026-10-18T12:00:00Z")
        failing += WEEK_BEFORE

        assertEquals(DataResult.Success(44), repository.refresh())
    }

    @Test
    fun `a failed index or newest week keeps the stored schedule`() = runTest {
        repository.refresh()
        failing += "observing-schedules"

        assertEquals(HTTP_UNAVAILABLE, (repository.refresh().failure as AppError.Http).code)
        failing.clear()
        failing += NEWEST
        assertEquals(HTTP_UNAVAILABLE, (repository.refresh().failure as AppError.Http).code)
        assertEquals(44, checkNotNull(repository.observeSchedule().first()).visits.size)
    }

    @Test
    fun `nothing is stored before the first refresh`() = runTest {
        assertNull(repository.observeSchedule().first())
    }

    private val DataResult<Int>.failure get() = (this as DataResult.Failure).error

    private companion object {
        const val INDEX_PATH = "jwst/science-execution/observing-schedules"
        const val NEWEST = "20261019_report_20261022.txt"
        const val WEEK_BEFORE = "20261012_report_20261008.txt"
        const val HTTP_UNAVAILABLE = 503
    }
}
