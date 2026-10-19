package com.radustavila.nasaexplorer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room behaviour is verified against a real (in-memory) SQLite database rather than a mock: the
 * interesting parts - conflict handling, partial updates, Flow invalidation - only exist in SQLite.
 */
@RunWith(RobolectricTestRunner::class)
class ApodDaoTest {

    private lateinit var database: NasaDatabase
    private lateinit var apodDao: ApodDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()
        apodDao = database.apodDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts a window and returns it newest first`() = runTest {
        apodDao.upsertPreservingLocalState(
            listOf(entity("2026-09-08"), entity("2026-09-10"), entity("2026-09-09")),
        )

        val dates = apodDao.observeFeed().first().map(ApodEntity::date)

        assertEquals(listOf("2026-09-10", "2026-09-09", "2026-09-08"), dates)
    }

    @Test
    fun `re-syncing the same dates updates rows instead of duplicating them`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10", title = "Original")))

        val insertedOnSecondSync = apodDao.upsertPreservingLocalState(
            listOf(entity("2026-09-10", title = "Corrected")),
        )

        assertEquals(0, insertedOnSecondSync)
        assertEquals(1, apodDao.observeCount().first())
        assertEquals("Corrected", apodDao.findByDate("2026-09-10")?.title)
    }

    @Test
    fun `counts and archive bounds are observable`() = runTest {
        apodDao.upsertPreservingLocalState(
            listOf(entity("2026-09-10"), entity("2026-09-09"), entity("2026-09-08")),
        )

        assertEquals(3, apodDao.observeCount().first())
        assertEquals("2026-09-08", apodDao.observeOldestDate().first())
        assertEquals("2026-09-10", apodDao.newestDate())
    }

    @Test
    fun `a day cached on its own is readable but stays out of the feed`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10")))

        apodDao.upsertPreservingLocalState(listOf(entity("2001-03-05", isInFeed = false)))

        assertEquals(listOf("2026-09-10"), apodDao.observeFeed().first().map(ApodEntity::date))
        assertEquals("Title 2001-03-05", apodDao.findByDate("2001-03-05")?.title)
        assertEquals(2, apodDao.observeCount().first())
    }

    @Test
    fun `paging past a day cached on its own pulls it into the feed`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2001-03-05", isInFeed = false)))

        apodDao.upsertPreservingLocalState(
            listOf(entity("2001-03-05", title = "Windowed"), entity("2001-03-06")),
        )

        val feed = apodDao.observeFeed().first()
        assertEquals(listOf("2001-03-06", "2001-03-05"), feed.map(ApodEntity::date))
        assertEquals("Windowed", apodDao.findByDate("2001-03-05")?.title)
    }

    @Test
    fun `a one-off fetch never drops a day back out of the feed`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10")))

        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10", isInFeed = false)))

        assertEquals(listOf("2026-09-10"), apodDao.observeFeed().first().map(ApodEntity::date))
    }

    @Test
    fun `the latest entry is the newest date in the cache`() = runTest {
        assertNull(apodDao.observeLatest().first())

        apodDao.upsertPreservingLocalState(
            listOf(entity("2026-09-08"), entity("2026-09-10"), entity("2026-09-09")),
        )

        assertEquals("2026-09-10", apodDao.observeLatest().first()?.date)
    }

    @Test
    fun `eviction trims old feed rows and stale one-off days but never a favourite`() = runTest {
        apodDao.upsertPreservingLocalState(
            listOf(
                entity("2026-09-10"),
                entity("2026-06-01"),
                entity("2026-05-31"),
                entity("2026-05-30"),
                entity("2001-03-05", isInFeed = false, syncedAt = FRESH),
                entity("2002-03-05", isInFeed = false, syncedAt = STALE),
            ),
        )
        database.favoriteDao().save(FavoriteEntity("apod", "2026-05-30", "Kept", null, 0))
        // A favourite of another source with the same id must not protect the APOD row.
        database.favoriteDao().save(FavoriteEntity("epic", "2026-05-31", "Other", null, 0))

        val deleted = apodDao.evict(cutoffDate = "2026-06-01", staleBeforeMillis = FRESH, favoriteSource = "apod")

        assertEquals(2, deleted)
        assertNull(apodDao.findByDate("2026-05-31"))
        assertNull(apodDao.findByDate("2002-03-05"))
        assertEquals(
            listOf("2026-09-10", "2026-06-01"),
            apodDao.observeFeed().first().map(ApodEntity::date),
        )
        // The favourite stays cached, but no longer as a lone row at the bottom of the feed.
        assertEquals(false, apodDao.findByDate("2026-05-30")?.isInFeed)
        assertEquals("Title 2001-03-05", apodDao.findByDate("2001-03-05")?.title)
    }

    private fun entity(
        date: String,
        title: String = "Title $date",
        isInFeed: Boolean = true,
        syncedAt: Long = 1_757_500_000_000,
    ) = ApodEntity(
        date = date,
        title = title,
        explanation = "Explanation",
        mediaType = "image",
        url = "https://apod.nasa.gov/$date.jpg",
        hdUrl = null,
        thumbnailUrl = null,
        copyright = null,
        cachedAtEpochMillis = 1_757_500_000_000,
        lastSyncedAtEpochMillis = syncedAt,
        isInFeed = isInFeed,
    )

    private companion object {
        const val STALE = 1_700_000_000_000L
        const val FRESH = 1_757_500_000_000L
    }
}
