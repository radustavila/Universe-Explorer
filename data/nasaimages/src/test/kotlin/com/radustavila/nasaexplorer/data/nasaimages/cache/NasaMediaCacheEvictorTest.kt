package com.radustavila.nasaexplorer.data.nasaimages.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesDatabase
import com.radustavila.nasaexplorer.data.nasaimages.local.RoomNasaImagesLocalDataSource
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NasaMediaCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val longAgo = now.minus(Duration.ofDays(30))

    private val favorites = FakeFavoritesRepository()
    private lateinit var database: NasaImagesDatabase
    private lateinit var local: RoomNasaImagesLocalDataSource
    private lateinit var evictor: NasaMediaCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaImagesDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        local = RoomNasaImagesLocalDataSource(database.nasaMediaDao(), io)
        evictor = NasaMediaCacheEvictor(database.nasaMediaDao(), favorites, io)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `rail items stay however old they are`() = runTest {
        local.replaceRails(mapOf(GalleryRail.WEBB to TestNasaMedia.images(3)), at = longAgo)

        assertEquals(0, evictor.evict(now))
        assertEquals(3, local.observeRails().first().getValue(GalleryRail.WEBB).size)
    }

    @Test
    fun `favourites stay, other old items go`() = runTest {
        val saved = TestNasaMedia.image(nasaId = "saved")
        val unsaved = TestNasaMedia.image(nasaId = "unsaved")
        local.store(saved, at = longAgo)
        local.store(unsaved, at = longAgo)
        favorites.seed(FavoriteItem(FavoriteSource.NASA_LIBRARY, "saved", saved.title, saved.previewUrl))
        // Same id under another source does not protect a library item.
        favorites.seed(FavoriteItem(FavoriteSource.APOD, "unsaved", "APOD", null))

        assertEquals(1, evictor.evict(now))
        assertNotNull(local.observeMedia("saved").first())
        assertNull(local.observeMedia("unsaved").first())
    }

    @Test
    fun `an item that dropped off a rail goes once it is past the grace period`() = runTest {
        val dropped = TestNasaMedia.image(nasaId = "dropped")
        local.replaceRails(mapOf(GalleryRail.WEBB to listOf(dropped)), at = longAgo)
        local.replaceRails(mapOf(GalleryRail.WEBB to TestNasaMedia.images(2)), at = now)

        assertEquals(0, evictor.evict(longAgo.plus(NasaMediaCacheEvictor.RETENTION).minusSeconds(1)))
        assertEquals(1, evictor.evict(now))
        assertNull(local.observeMedia("dropped").first())
    }

    @Test
    fun `recent unreferenced items are kept`() = runTest {
        local.store(TestNasaMedia.image(nasaId = "recent"), at = now.minus(Duration.ofDays(1)))

        assertEquals(0, evictor.evict(now))
    }
}
