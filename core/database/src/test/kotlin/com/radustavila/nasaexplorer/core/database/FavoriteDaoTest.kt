package com.radustavila.nasaexplorer.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.database.repository.RoomFavoritesRepository
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeOfflineFavoriteImages
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    private lateinit var database: NasaDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var repository: RoomFavoritesRepository
    private val offlineImages = FakeOfflineFavoriteImages()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaDatabase::class.java,
        ).allowMainThreadQueries().build()
        favoriteDao = database.favoriteDao()
        repository = RoomFavoritesRepository(favoriteDao, offlineImages, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `items of different sources with the same id are distinct favourites`() = runTest {
        repository.save(item(FavoriteSource.APOD, "2026-09-10", savedAt = 1))
        repository.save(item(FavoriteSource.EPIC, "2026-09-10", savedAt = 2))

        assertEquals(2, repository.observeCount().first())
        assertEquals(setOf("2026-09-10"), repository.observeFavoriteIds(FavoriteSource.APOD).first())

        repository.remove(FavoriteSource.APOD, "2026-09-10")

        assertFalse(repository.observeIsFavorite(FavoriteSource.APOD, "2026-09-10").first())
        assertTrue(repository.observeIsFavorite(FavoriteSource.EPIC, "2026-09-10").first())
    }

    @Test
    fun `favourites are listed most recently saved first`() = runTest {
        repository.save(item(FavoriteSource.APOD, "old", savedAt = 1))
        repository.save(item(FavoriteSource.NASA_LIBRARY, "new", savedAt = 2))

        assertEquals(listOf("new", "old"), repository.observeFavorites().first().map { it.itemId })
    }

    @Test
    fun `saving again refreshes the snapshot but keeps the original save time`() = runTest {
        repository.save(item(FavoriteSource.MARS, "pic", title = "Old", savedAt = 1))

        repository.save(item(FavoriteSource.MARS, "pic", title = "New", savedAt = 99))

        val saved = repository.observeFavorites().first().single()
        assertEquals("New", saved.title)
        assertEquals(Instant.ofEpochMilli(1), saved.savedAt)
    }

    @Test
    fun `a row from an unknown source is skipped rather than crashing the list`() = runTest {
        favoriteDao.save(FavoriteEntity("from-the-future", "x", "?", null, 5))
        repository.save(item(FavoriteSource.APOD, "2026-09-10", savedAt = 1))

        assertEquals(listOf(FavoriteSource.APOD), repository.observeFavorites().first().map { it.source })
    }

    @Test
    fun `the favourite flag of one item emits on every toggle`() = runTest {
        repository.observeIsFavorite(FavoriteSource.EPIC, "img").test {
            assertFalse(awaitItem())
            repository.save(item(FavoriteSource.EPIC, "img", savedAt = 1))
            assertTrue(awaitItem())
            repository.remove(FavoriteSource.EPIC, "img")
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `saving queues an offline copy of the image and removing discards it`() = runTest {
        repository.save(item(FavoriteSource.MARS, "pic", savedAt = 1))
        repository.save(item(FavoriteSource.EXOPLANET, "no-image", savedAt = 2).copy(imageUrl = null))

        repository.remove(FavoriteSource.MARS, "pic")

        assertEquals(listOf(FavoriteSource.MARS to "pic"), offlineImages.requestedDownloads)
        assertEquals(listOf(FavoriteSource.MARS to "pic"), offlineImages.discarded)
    }

    @Test
    fun `an offline copy is recorded only for the image the snapshot still shows`() = runTest {
        repository.save(item(FavoriteSource.APOD, "2026-09-10", savedAt = 1))

        assertEquals(0, favoriteDao.setLocalImage("apod", "2026-09-10", "https://other.org/x.jpg", "apod/x.jpg", 10))
        assertEquals(
            1,
            favoriteDao.setLocalImage("apod", "2026-09-10", "https://example.org/2026-09-10.jpg", "apod/a.jpg", 42),
        )

        assertEquals("apod/a.jpg", favoriteDao.find("apod", "2026-09-10")?.localImagePath)
        assertEquals(42L, favoriteDao.observeLocalImageBytes().first())
        assertEquals(
            listOf("https://example.org/2026-09-10.jpg"),
            favoriteDao.observeLocalImages().first().map { it.imageUrl },
        )
        assertTrue(favoriteDao.findWithoutLocalImage().isEmpty())
    }

    @Test
    fun `a new snapshot image invalidates the offline copy, the same image keeps it`() = runTest {
        repository.save(item(FavoriteSource.APOD, "day", savedAt = 1))
        favoriteDao.setLocalImage("apod", "day", "https://example.org/day.jpg", "apod/day.jpg", 42)

        repository.save(item(FavoriteSource.APOD, "day", title = "Renamed", savedAt = 2))
        assertEquals("apod/day.jpg", favoriteDao.find("apod", "day")?.localImagePath)

        repository.save(item(FavoriteSource.APOD, "day", savedAt = 3).copy(imageUrl = "https://example.org/new.jpg"))
        val row = requireNotNull(favoriteDao.find("apod", "day"))
        assertNull(row.localImagePath)
        assertEquals(0L, row.localImageBytes)
        assertEquals(listOf("day"), favoriteDao.findWithoutLocalImage().map { it.itemId })
        assertEquals(0L, favoriteDao.observeLocalImageBytes().first())
    }

    private fun item(source: FavoriteSource, id: String, title: String = "Title", savedAt: Long) = FavoriteItem(
        source = source,
        itemId = id,
        title = title,
        imageUrl = "https://example.org/$id.jpg",
        savedAt = Instant.ofEpochMilli(savedAt),
    )
}
