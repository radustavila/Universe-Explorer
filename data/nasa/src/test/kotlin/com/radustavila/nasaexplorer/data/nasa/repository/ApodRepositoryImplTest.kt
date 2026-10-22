package com.radustavila.nasaexplorer.data.nasa.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.repository.RoomFavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.ApodBatchResult
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeOfflineFavoriteImages
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.data.nasa.local.ApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.local.RoomApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.ApodRemoteDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.dto.ApodDto
import java.time.LocalDate
import kotlinx.coroutines.delay
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
    private lateinit var favorites: RoomFavoritesRepository
    private lateinit var localDataSource: CountingLocalDataSource

    private val remoteDataSource = ScriptedRemoteDataSource()
    private val syncMetadata = FakeSyncMetadataRepository()
    private val timeProvider = FakeTimeProvider(currentDate = TODAY)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()

        favorites = RoomFavoritesRepository(
            favoriteDao = database.favoriteDao(),
            offlineImages = FakeOfflineFavoriteImages(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        localDataSource = CountingLocalDataSource(
            RoomApodLocalDataSource(apodDao = database.apodDao(), ioDispatcher = UnconfinedTestDispatcher()),
        )
        repository = ApodRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            syncMetadataRepository = syncMetadata,
            favoritesRepository = favorites,
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
        favorites.save(apodFavorite(TODAY))

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
    fun `fetching one day caches just that day`() = runTest {
        val archiveDate = LocalDate.of(2001, 3, 5)
        remoteDataSource.singleResult = DataResult.Success(dtosFor(archiveDate, archiveDate).first())

        val result = repository.fetchApod(archiveDate)

        assertEquals(DataResult.Success(Unit), result)
        assertEquals(archiveDate, remoteDataSource.lastRequestedDate)
        assertEquals(1, repository.observeCachedCount().first())
        assertEquals("Apod $archiveDate", repository.observeApod(archiveDate).first()?.title)
    }

    @Test
    fun `a day fetched on its own is readable but absent from the feed`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()

        val archiveDate = LocalDate.of(2001, 3, 5)
        remoteDataSource.singleResult = DataResult.Success(dtosFor(archiveDate, archiveDate).first())
        repository.fetchApod(archiveDate)

        // Otherwise a lone 2001 entry would sit directly under this week's window.
        assertEquals(7, repository.observeFeed().first().size)
        assertEquals(archiveDate, repository.observeApod(archiveDate).first()?.date)
    }

    @Test
    fun `paging past a day fetched on its own pulls it into the feed`() = runTest {
        val archiveDate = LocalDate.of(2001, 3, 5)
        remoteDataSource.singleResult = DataResult.Success(dtosFor(archiveDate, archiveDate).first())
        repository.fetchApod(archiveDate)

        syncMetadata.setOldestRequestedDate(archiveDate.plusDays(1))
        remoteDataSource.rangeResult =
            DataResult.Success(dtosFor(archiveDate.minusDays(6), archiveDate))
        repository.loadOlderWindow()

        assertEquals(7, repository.observeFeed().first().size)
    }

    @Test
    fun `fetching one day does not move the paging cursor`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()

        val archiveDate = LocalDate.of(2001, 3, 5)
        remoteDataSource.singleResult = DataResult.Success(dtosFor(archiveDate, archiveDate).first())
        repository.fetchApod(archiveDate)

        // Paging still continues from the newest window; the one-off day is a hole, not a frontier.
        assertEquals(TODAY.minusDays(6), syncMetadata.currentOldestRequested)
    }

    @Test
    fun `a failing single-day fetch leaves the cache untouched`() = runTest {
        remoteDataSource.singleResult = DataResult.Failure(AppError.Http(code = 404))

        val result = repository.fetchApod(LocalDate.of(2001, 3, 5))

        assertEquals(DataResult.Failure(AppError.Http(code = 404)), result)
        assertEquals(0, repository.observeCachedCount().first())
    }

    @Test
    fun `a write outside the feed does not make the feed emit again`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()

        repository.observeFeed().test {
            assertEquals(7, awaitItem().size)

            val archiveDate = LocalDate.of(2001, 3, 5)
            remoteDataSource.singleResult = DataResult.Success(dtosFor(archiveDate, archiveDate).first())
            repository.fetchApod(archiveDate)
            remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY, titlePrefix = "New"))
            repository.refreshRecent()

            // The next emission is the changed feed, not a copy caused by the one-off day.
            assertEquals("New $TODAY", awaitItem().first().title)
        }
    }

    @Test
    fun `a batch of days is fetched a few at a time and cached in one write, outside the feed`() = runTest {
        remoteDataSource.answerEveryDate = true
        val days = (2001..2010).map { year -> LocalDate.of(year, 9, 10) }
        remoteDataSource.singleDelayMillis = 100

        val result = repository.fetchApods(days, maxConcurrent = 4)

        assertEquals(ApodBatchResult(cached = days.toSet()), result)
        assertEquals(4, remoteDataSource.maxInFlight)
        assertEquals(days.toSet(), remoteDataSource.requestedDates.toSet())
        assertEquals(1, localDataSource.upsertCount)
        assertEquals(10, repository.observeCachedCount().first())
        assertTrue(repository.observeFeed().first().isEmpty())
    }

    @Test
    fun `a batch reports the days without an entry and caches the rest`() = runTest {
        val gap = LocalDate.of(2005, 9, 10)
        val malformed = LocalDate.of(2006, 9, 10)
        val days = listOf(LocalDate.of(2007, 9, 10), malformed, gap)
        remoteDataSource.answerEveryDate = true
        remoteDataSource.singleResults[gap] = DataResult.Failure(AppError.Http(code = 404))
        remoteDataSource.singleResults[malformed] = DataResult.Success(ApodDto(date = "not a date"))

        val result = repository.fetchApods(days, maxConcurrent = 4)

        assertEquals(setOf(LocalDate.of(2007, 9, 10)), result.cached)
        assertEquals(mapOf(gap to AppError.Http(code = 404), malformed to AppError.EmptyResponse), result.failed)
        assertEquals(1, repository.observeCachedCount().first())
    }

    @Test
    fun `a connectivity failure stops the batch but keeps what arrived`() = runTest {
        val days = (2010 downTo 2001).map { year -> LocalDate.of(year, 9, 10) }
        remoteDataSource.answerEveryDate = true
        remoteDataSource.singleResults[LocalDate.of(2009, 9, 10)] = DataResult.Failure(AppError.Timeout)

        val result = repository.fetchApods(days, maxConcurrent = 1)

        assertEquals(setOf(LocalDate.of(2010, 9, 10)), result.cached)
        assertEquals(mapOf(LocalDate.of(2009, 9, 10) to AppError.Timeout), result.failed)
        assertEquals(2, remoteDataSource.requestedDates.size)
        assertEquals(1, repository.observeCachedCount().first())
    }

    @Test
    fun `a favourite saved in the shared table marks the entry in every read, offline`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(1), TODAY))
        repository.refreshRecent()
        remoteDataSource.rangeResult = DataResult.Failure(AppError.NoConnection)

        repository.observeFeed().test {
            assertTrue(awaitItem().none(Apod::isFavorite))

            favorites.save(apodFavorite(TODAY))

            assertEquals(listOf(TODAY), awaitItem().filter(Apod::isFavorite).map(Apod::date))
        }
        assertTrue(repository.observeApod(TODAY).first()?.isFavorite == true)
        assertTrue(repository.observeLatestApod().first()?.isFavorite == true)
    }

    @Test
    fun `a favourite of another source with the same id does not mark the entry`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY, TODAY))
        repository.refreshRecent()

        favorites.save(apodFavorite(TODAY).copy(source = FavoriteSource.EPIC))

        assertTrue(repository.observeApod(TODAY).first()?.isFavorite == false)
    }

    @Test
    fun `search finds synced and single-day entries in the cache only`() = runTest {
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY.minusDays(6), TODAY))
        repository.refreshRecent()
        val archiveDate = LocalDate.of(2001, 3, 5)
        remoteDataSource.singleResult = DataResult.Success(
            dtosFor(archiveDate, archiveDate).first().copy(title = "Comet Hale-Bopp"),
        )
        repository.fetchApod(archiveDate)

        repository.searchCached("hale").test {
            assertEquals(listOf(archiveDate), awaitItem().map(Apod::date))
        }
        assertEquals(8, repository.searchCached("explanation").first().size)
        assertTrue(repository.searchCached("\"(*-").first().isEmpty())
    }

    @Test
    fun `the same day in earlier years comes from the cache, newest first`() = runTest {
        listOf(LocalDate.of(2001, 9, 10), LocalDate.of(2015, 9, 10), LocalDate.of(2015, 9, 11))
            .forEach { date ->
                remoteDataSource.singleResult = DataResult.Success(dtosFor(date, date).first())
                repository.fetchApod(date)
            }
        remoteDataSource.rangeResult = DataResult.Success(dtosFor(TODAY, TODAY))
        repository.refreshRecent()

        assertEquals(
            listOf(LocalDate.of(2015, 9, 10), LocalDate.of(2001, 9, 10)),
            repository.observeSameDayInEarlierYears(TODAY).first().map(Apod::date),
        )
    }
    private fun apodFavorite(date: LocalDate) = FavoriteItem(
        source = FavoriteSource.APOD,
        itemId = date.toString(),
        title = "Apod $date",
        imageUrl = null,
    )

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

        var singleResult: DataResult<ApodDto> = DataResult.Failure(AppError.Unknown("not scripted"))
        var lastRequestedDate: LocalDate? = null
            private set

        /** Per-date answers; any other date gets [singleResult], or a valid entry when [answerEveryDate]. */
        var answerEveryDate = false
        val singleResults: MutableMap<LocalDate, DataResult<ApodDto>> = mutableMapOf()
        var singleDelayMillis = 0L
        val requestedDates = mutableListOf<LocalDate>()
        var maxInFlight = 0
            private set
        private var inFlight = 0

        override suspend fun fetchSingle(date: LocalDate): DataResult<ApodDto> {
            lastRequestedDate = date
            requestedDates += date
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(singleDelayMillis)
            inFlight--
            return singleResults[date] ?: if (answerEveryDate) DataResult.Success(dtoFor(date)) else singleResult
        }

        private fun dtoFor(date: LocalDate) = ApodDto(
            date = date.toString(),
            title = "Apod $date",
            explanation = "Explanation for $date",
            mediaType = "image",
            url = "https://apod.nasa.gov/$date.jpg",
        )
    }

    /** Counts writes, so a batch can be shown to write once. */
    private class CountingLocalDataSource(
        private val delegate: ApodLocalDataSource,
    ) : ApodLocalDataSource by delegate {
        var upsertCount = 0
            private set

        override suspend fun upsertPreservingLocalState(entities: List<ApodEntity>): DataResult<Int> {
            upsertCount++
            return delegate.upsertPreservingLocalState(entities)
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 10)
    }
}
