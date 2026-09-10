package com.radustavila.nasaexplorer.data.nasa.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.data.nasa.local.RoomApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.ApodRemoteDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Repository behaviour against a real Room database and a scripted remote source: this is where the
 * offline-first contract (reads from the cache, writes from the network, favourites preserved) is
 * actually verified.
 */
@RunWith(RobolectricTestRunner::class)
class ApodRepositoryImplTest {

    private lateinit var database: NasaDatabase
    private lateinit var repository: ApodRepositoryImpl

    private val remoteDataSource = ScriptedRemoteDataSource()
    private val syncMetadata = FakeSyncMetadataRepository()
    private val timeProvider = FakeTimeProvider(currentDate = TODAY)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()

        repository = ApodRepositoryImpl(
            localDataSource = RoomApodLocalDataSource(
                apodDao = database.apodDao(),
                ioDispatcher = UnconfinedTestDispatcher(),
            ),
            remoteDataSource = remoteDataSource,
            syncMetadataRepository = syncMetadata,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `refresh requests the last seven days and caches them`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))

        val result = repository.refreshRecent()

        assertEquals(DataResult.Success(7), result)
        assertEquals(LocalDate.of(2026, 9, 4) to TODAY, remoteDataSource.lastRequestedRange)
        assertEquals(7, repository.observeCachedCount().first())
    }

    @Test
    fun `refresh records how far back the feed has been requested`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))

        repository.refreshRecent()

        assertEquals(TODAY.minusDays(6), syncMetadata.currentOldestRequested)
    }

    @Test
    fun `a rejected window is retried without today before giving up`() = runTest {
        // NASA answers 400 until the current day's picture is published.
        remoteDataSource.rangeResult = DataResult.Failure(AppError.Http(code = 400))
        remoteDataSource.rangeResultOnSecondCall =
            DataResult.Success(dtosFor(TODAY.minusDays(7), TODAY.minusDays(1)))

        val result = repository.refreshRecent()

        assertEquals(DataResult.Success(7), result)
        assertEquals(TODAY.minusDays(7) to TODAY.minusDays(1), remoteDataSource.lastRequestedRange)
    }

    @Test
    fun `a failing refresh leaves the cache untouched`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()

        remoteDataSource.rangeResult = DataResult.Failure(AppError.Timeout)
        val result = repository.refreshRecent()

        assertEquals(DataResult.Failure(AppError.Timeout), result)
        assertEquals(7, repository.observeCachedCount().first())
    }

    @Test
    fun `an all-unusable payload is reported as an empty response`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(listOf(ApodDto(date = "nonsense")))

        val result = repository.refreshRecent()

        assertEquals(DataResult.Failure(AppError.EmptyResponse), result)
    }

    @Test
    fun `loading older entries walks one window further back`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()

        remoteDataSource.rangeResult =
            DataResult.Success(dtosFor(TODAY.minusDays(13), TODAY.minusDays(7)))
        val result = repository.loadOlderWindow()

        assertEquals(DataResult.Success(7), result)
        assertEquals(TODAY.minusDays(13) to TODAY.minusDays(7), remoteDataSource.lastRequestedRange)
        assertEquals(14, repository.observeCachedCount().first())
        assertEquals(TODAY.minusDays(13), syncMetadata.currentOldestRequested)
    }

    @Test
    fun `paging never asks for dates before the first published apod`() = runTest {
        syncMetadata.setOldestRequestedDate(Apod.FIRST_PUBLICATION_DATE.plusDays(3))
        remoteDataSource.rangeResult = DataResult.Success(
            dtosFor(Apod.FIRST_PUBLICATION_DATE, Apod.FIRST_PUBLICATION_DATE.plusDays(2)),
        )

        repository.loadOlderWindow()

        assertEquals(
            Apod.FIRST_PUBLICATION_DATE to Apod.FIRST_PUBLICATION_DATE.plusDays(2),
            remoteDataSource.lastRequestedRange,
        )
    }

    @Test
    fun `re-syncing an already cached window neither duplicates rows nor clears favorites`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()
        repository.setFavorite(date = TODAY, isFavorite = true)

        remoteDataSource.rangeResult = DataResult.Success(
            dtosFor(TODAY.minusDays(6), TODAY, titlePrefix = "Updated"),
        )
        repository.refreshRecent()

        assertEquals(7, repository.observeCachedCount().first())
        val today = repository.observeApod(TODAY).first()
        assertEquals("Updated $TODAY", today?.title)
        assertTrue(today?.isFavorite == true)
    }

    @Test
    fun `favorites are observable and require no network`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(1), TODAY))
        repository.refreshRecent()
        remoteDataSource.rangeResult = DataResult.Failure(AppError.NoConnection)

        repository.observeFavorites().test {
            assertTrue(awaitItem().isEmpty())

            repository.setFavorite(date = TODAY, isFavorite = true)

            assertEquals(listOf(TODAY), awaitItem().map(Apod::date))
        }
    }

    @Test
    fun `favoriting an entry that is not cached fails with a database error`() = runTest {
        val result = repository.setFavorite(date = TODAY, isFavorite = true)

        assertTrue((result as DataResult.Failure).error is AppError.Database)
    }

    private fun dtosFor(
        start: LocalDate,
        end: LocalDate,
        titlePrefix: String = "Apod",
    ): List<ApodDto> = generateSequence(start) { previous ->
        previous.plusDays(1).takeIf { !it.isAfter(end) }
    }.map { date ->
        ApodDto(
            date = date.toString(),
            title = "$titlePrefix $date",
            explanation = "Explanation for $date",
            mediaType = "image",
            url = "https://apod.nasa.gov/$date.jpg",
        )
    }.toList()

    private class ScriptedRemoteDataSource : ApodRemoteDataSource {
        var rangeResult: DataResult<List<ApodDto>> = DataResult.Success(emptyList())
        var rangeResultOnSecondCall: DataResult<List<ApodDto>>? = null
        var lastRequestedRange: Pair<LocalDate, LocalDate>? = null
            private set

        private var callCount = 0

        override suspend fun fetchRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): DataResult<List<ApodDto>> {
            lastRequestedRange = startDate to endDate
            callCount++
            return if (callCount > 1) rangeResultOnSecondCall ?: rangeResult else rangeResult
        }

        override suspend fun fetchSingle(date: LocalDate): DataResult<ApodDto> =
            DataResult.Failure(AppError.Unknown("not used"))
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 10)
    }
}
