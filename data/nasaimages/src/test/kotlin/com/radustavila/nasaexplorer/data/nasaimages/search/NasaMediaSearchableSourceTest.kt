package com.radustavila.nasaexplorer.data.nasaimages.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesDatabase
import com.radustavila.nasaexplorer.data.nasaimages.local.RoomNasaImagesLocalDataSource
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NasaMediaSearchableSourceTest {

    private lateinit var database: NasaImagesDatabase
    private lateinit var local: RoomNasaImagesLocalDataSource
    private lateinit var source: NasaMediaSearchableSource

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NasaImagesDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        local = RoomNasaImagesLocalDataSource(database.nasaMediaDao(), io)
        source = NasaMediaSearchableSource(database.nasaMediaDao(), io)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `finds cached items by every word of the query, in title, description or keywords, newest first`() = runTest {
        val now = Instant.parse("2026-10-19T12:00:00Z")
        listOf(
            TestNasaMedia.image(nasaId = "old", title = "Webb Deep Field").copy(dateCreated = LocalDate.of(2022, 7, 12)),
            TestNasaMedia.image(nasaId = "new", title = "Cosmic Cliffs").copy(dateCreated = LocalDate.of(2025, 1, 1)),
            TestNasaMedia.image(nasaId = "other", title = "Apollo 11").copy(keywords = listOf("Moon"), description = "Moon walk"),
        ).forEach { local.store(it, at = now) }

        val hits = requireNotNull(source.search("webb matter", limit = 10).getOrNull())

        // Both carry "Webb" and "Dark Matter" in their keywords or description; Apollo does not.
        assertEquals(listOf("new", "old"), hits.map { it.id })
        assertEquals(SearchTarget.NasaMediaItem("new"), hits.first().target)
        assertEquals(LocalDate.of(2025, 1, 1), hits.first().date)
    }

    @Test
    fun `respects the limit and finds nothing for an unknown word`() = runTest {
        TestNasaMedia.images(5).forEach { local.store(it, at = Instant.EPOCH) }

        assertEquals(2, source.search("dark", limit = 2).getOrNull()?.size)
        assertEquals(emptyList<Any>(), source.search("zzzz", limit = 10).getOrNull())
    }
}
