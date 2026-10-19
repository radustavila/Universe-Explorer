package com.radustavila.nasaexplorer.data.mars.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.data.mars.local.MarsDatabase
import com.radustavila.nasaexplorer.data.mars.local.RoomMarsLocalDataSource
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
class MarsPhotoCacheEvictorTest {

    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private val longAgo = now.minus(Duration.ofDays(30))

    private val favorites = FakeFavoritesRepository()
    private lateinit var database: MarsDatabase
    private lateinit var local: RoomMarsLocalDataSource
    private lateinit var evictor: MarsPhotoCacheEvictor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MarsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        local = RoomMarsLocalDataSource(database.marsPhotoDao(), io)
        evictor = MarsPhotoCacheEvictor(database.marsPhotoDao(), favorites, io)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `old photos go, recent ones stay`() = runTest {
        local.store(listOf(TestMarsPhotos.curiosity(id = "old", sol = 5000)), at = longAgo)
        local.store(listOf(TestMarsPhotos.curiosity(id = "recent", sol = 5040)), at = now.minus(Duration.ofDays(2)))
        local.store(listOf(TestMarsPhotos.curiosity(id = "newest", sol = 5048)), at = now)

        assertEquals(1, evictor.evict(now))
        assertNull(local.observePhoto("old").first())
        assertNotNull(local.observePhoto("recent").first())
    }

    @Test
    fun `favourites stay however old they are`() = runTest {
        local.store(
            listOf(
                TestMarsPhotos.curiosity(id = "saved", sol = 5000),
                TestMarsPhotos.curiosity(id = "unsaved", sol = 5000),
                TestMarsPhotos.curiosity(id = "newest", sol = 5048),
            ),
            at = longAgo,
        )
        favorites.seed(FavoriteItem(FavoriteSource.MARS, "saved", "Saved", null))
        // The same id under another source does not protect a rover photo.
        favorites.seed(FavoriteItem(FavoriteSource.APOD, "unsaved", "APOD", null))

        assertEquals(1, evictor.evict(now))
        assertNotNull(local.observePhoto("saved").first())
        assertNull(local.observePhoto("unsaved").first())
    }

    @Test
    fun `each rover keeps its newest sol after a long time offline`() = runTest {
        local.store(
            listOf(
                TestMarsPhotos.curiosity(id = "c-old", sol = 5000),
                TestMarsPhotos.curiosity(id = "c-newest", sol = 5048),
                TestMarsPhotos.perseverance(id = "p-newest", sol = 1650),
            ),
            at = longAgo,
        )

        assertEquals(1, evictor.evict(now))
        assertEquals(listOf("c-newest"), local.observeRover(MarsRover.CURIOSITY).first().map { it.id })
        assertEquals(listOf("p-newest"), local.observeRover(MarsRover.PERSEVERANCE).first().map { it.id })
    }

    @Test
    fun `nothing goes before the retention period`() = runTest {
        local.store(listOf(TestMarsPhotos.curiosity(id = "old", sol = 5000)), at = longAgo)
        local.store(listOf(TestMarsPhotos.curiosity(id = "newest", sol = 5048)), at = longAgo)

        assertEquals(0, evictor.evict(longAgo.plus(MarsPhotoCacheEvictor.RETENTION).minusSeconds(1)))
    }
}
