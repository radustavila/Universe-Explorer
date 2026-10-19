package com.radustavila.nasaexplorer.data.nasa.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApodCacheEvictorTest {

    private lateinit var database: NasaDatabase
    private val metadata = FakeSyncMetadataRepository(initialOldestRequested = LocalDate.of(2025, 1, 1))
    private lateinit var evictor: ApodCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()
        evictor = ApodCacheEvictor(database.apodDao(), metadata, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `keeps 90 days below the newest entry and every favourite, and moves the paging cursor up`() =
        runTest {
            val newest = LocalDate.of(2026, 10, 19)
            val edge = newest.minusDays(ApodCacheEvictor.FEED_RETENTION_DAYS)
            database.apodDao().upsertPreservingLocalState(
                listOf(row(newest), row(edge), row(edge.minusDays(1)), row(edge.minusDays(2))),
            )
            database.favoriteDao().save(FavoriteEntity("apod", edge.minusDays(2).toString(), "Fav", null, 0))

            val deleted = evictor.evict(NOW)

            assertEquals(1, deleted)
            assertNull(database.apodDao().findByDate(edge.minusDays(1).toString()))
            assertEquals(
                listOf(newest.toString(), edge.toString()),
                database.apodDao().observeFeed().first().map(ApodEntity::date),
            )
            assertEquals(edge.minusDays(2).toString(), database.apodDao().findByDate(edge.minusDays(2).toString())?.date)
            assertEquals(edge, metadata.currentOldestRequested)
        }

    @Test
    fun `a device that has been offline for months keeps its last quarter of the feed`() = runTest {
        val newest = LocalDate.of(2026, 1, 1)
        database.apodDao().upsertPreservingLocalState(listOf(row(newest), row(newest.minusDays(30))))

        val deleted = evictor.evict(NOW)

        assertEquals(0, deleted)
        assertEquals(2, database.apodDao().observeCount().first())
    }

    @Test
    fun `an empty cache is left alone`() = runTest {
        assertEquals(0, evictor.evict(NOW))
        assertEquals(LocalDate.of(2025, 1, 1), metadata.currentOldestRequested)
    }

    private fun row(date: LocalDate) = ApodEntity(
        date = date.toString(),
        title = "Title $date",
        explanation = "",
        mediaType = "image",
        url = "https://apod.nasa.gov/$date.jpg",
        hdUrl = null,
        thumbnailUrl = null,
        copyright = null,
        cachedAtEpochMillis = NOW.toEpochMilli(),
        lastSyncedAtEpochMillis = NOW.toEpochMilli(),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-19T12:00:00Z")
    }
}
