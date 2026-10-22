package com.radustavila.nasaexplorer.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The app targets a newer SDK than Robolectric emulates, and its Hilt application is not needed here.
@Config(sdk = [36], application = android.app.Application::class)
class DatabaseStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun storage(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        DatabaseStorage(context, UnconfinedTestDispatcher(scheduler))

    /** A database holding [rows] rows of about a kilobyte each. */
    private fun database(name: String, rows: Int) {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS item (id INTEGER PRIMARY KEY, body TEXT)")
            repeat(rows) { db.execSQL("INSERT INTO item (body) VALUES (?)", arrayOf("x".repeat(1_000))) }
        }
    }

    private fun deleteAll(name: String) {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("DELETE FROM item")
        }
    }

    @Test
    fun `counts the feeds' mirrors as cached data and the main database as the library`() = runTest {
        database("launches.db", rows = 100)
        database("earth_events.db", rows = 100)
        database(NasaDatabase.NAME, rows = 50)

        val entries = storage(testScheduler).measure().associate { it.kind to it.bytes }

        assertTrue("${entries[StorageKind.CACHED_DATA]}", entries.getValue(StorageKind.CACHED_DATA) > 200_000)
        assertTrue("${entries[StorageKind.LIBRARY]}", entries.getValue(StorageKind.LIBRARY) in 50_000..200_000)
    }

    @Test
    fun `emptied tables shrink the measure although SQLite keeps its file`() = runTest {
        database("mars.db", rows = 200)
        val before = storage(testScheduler).measure().first { it.kind == StorageKind.CACHED_DATA }.bytes

        deleteAll("mars.db")
        val after = storage(testScheduler).measure().first { it.kind == StorageKind.CACHED_DATA }.bytes

        assertTrue("$before -> $after", after < before / 10)
    }

    @Test
    fun `a database that does not exist yet takes nothing`() = runTest {
        assertEquals(0L, storage(testScheduler).measure().first { it.kind == StorageKind.LIBRARY }.bytes)
    }

    @Test
    fun `every mirror database the app opens is listed`() {
        assertEquals(
            setOf(
                "launches.db", "earth_events.db", "exoplanets.db", "mars.db", "nasa_images.db",
                "small_bodies.db", "space_weather.db", "sky.db", "news.db",
            ),
            DatabaseStorage.MIRROR_DATABASES.toSet(),
        )
    }
}
