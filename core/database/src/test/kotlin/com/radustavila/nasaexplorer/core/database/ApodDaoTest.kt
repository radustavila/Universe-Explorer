package com.radustavila.nasaexplorer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `a sync never clears a local favorite`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10")))
        apodDao.setFavorite(date = "2026-09-10", isFavorite = true)

        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10", title = "Refreshed")))

        val row = apodDao.findByDate("2026-09-10")
        assertEquals("Refreshed", row?.title)
        assertTrue(row?.isFavorite == true)
    }

    @Test
    fun `favorites flow emits on every toggle`() = runTest {
        apodDao.upsertPreservingLocalState(listOf(entity("2026-09-10"), entity("2026-09-09")))

        apodDao.observeFavorites().test {
            assertTrue(awaitItem().isEmpty())

            apodDao.setFavorite(date = "2026-09-10", isFavorite = true)
            assertEquals(listOf("2026-09-10"), awaitItem().map(ApodEntity::date))

            apodDao.setFavorite(date = "2026-09-10", isFavorite = false)
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun `counts and archive bounds are observable`() = runTest {
        apodDao.upsertPreservingLocalState(
            listOf(entity("2026-09-10"), entity("2026-09-09"), entity("2026-09-08")),
        )
        apodDao.setFavorite(date = "2026-09-09", isFavorite = true)

        assertEquals(3, apodDao.observeCount().first())
        assertEquals(1, apodDao.observeFavoritesCount().first())
        assertEquals("2026-09-08", apodDao.observeOldestDate().first())
        assertEquals("2026-09-10", apodDao.newestDate())
    }

    @Test
    fun `updating an unknown date changes nothing`() = runTest {
        val updatedRows = apodDao.setFavorite(date = "1999-01-01", isFavorite = true)

        assertEquals(0, updatedRows)
    }

    private fun entity(date: String, title: String = "Title $date") = ApodEntity(
        date = date,
        title = title,
        explanation = "Explanation",
        mediaType = "image",
        url = "https://apod.nasa.gov/$date.jpg",
        hdUrl = null,
        thumbnailUrl = null,
        copyright = null,
        cachedAtEpochMillis = 1_757_500_000_000,
    )
}
