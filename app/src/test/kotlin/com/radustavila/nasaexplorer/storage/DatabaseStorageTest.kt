package com.radustavila.nasaexplorer.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// The app targets a newer SDK than Robolectric emulates, and its Hilt application is not needed here.
@Config(sdk = [36], application = android.app.Application::class)
class DatabaseStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun write(name: String, bytes: Int) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    @Test
    fun `counts the feeds' mirrors as cached data and the main database as the library, with their logs`() = runTest {
        write("launches.db", 1_000)
        write("launches.db-wal", 200)
        write("earth_events.db", 3_000)
        write(NasaDatabase.NAME, 50_000)
        write("${NasaDatabase.NAME}-wal", 4_000)

        val entries = DatabaseStorage(context, UnconfinedTestDispatcher(testScheduler)).measure()

        assertEquals(
            listOf(StorageEntry(StorageKind.CACHED_DATA, 4_200), StorageEntry(StorageKind.LIBRARY, 54_000)),
            entries,
        )
    }

    @Test
    fun `every mirror database the app opens is listed`() {
        assertEquals(
            setOf(
                "launches.db", "earth_events.db", "exoplanets.db", "mars.db", "nasa_images.db",
                "small_bodies.db", "space_weather.db", "sky.db",
            ),
            DatabaseStorage.MIRROR_DATABASES.toSet(),
        )
    }
}
