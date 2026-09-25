package com.radustavila.nasaexplorer.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.migration.NasaDatabaseMigrations
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
 * The cache is the offline experience and favourites live nowhere else, so an upgrade that loses
 * rows is a data-loss bug. This builds a real version-1 database on disk, opens it with the current
 * Room definition, and checks what survived.
 *
 * Opening through Room also validates the post-migration schema against the generated schema of the
 * current version: a migration that drifts from the entity definition fails here rather than in
 * production.
 */
@RunWith(RobolectricTestRunner::class)
class NasaDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var database: NasaDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun `migrating from 1 to 2 preserves cached entries and their favorite flag`() = runTest {
        createVersion1Database()

        val dao = openWithMigrations().apodDao()

        val favorite = requireNotNull(dao.findByDate("2026-09-10"))
        assertEquals("Spiral Galaxy NGC 1300", favorite.title)
        assertEquals("https://apod.nasa.gov/apod/image/ngc1300.jpg", favorite.url)
        assertEquals(CACHED_AT_MILLIS, favorite.cachedAtEpochMillis)
        assertTrue(favorite.isFavorite)

        val plain = requireNotNull(dao.findByDate("2026-09-09"))
        assertEquals("Flight Through the Orion Nebula", plain.title)
        assertTrue(!plain.isFavorite)
    }

    @Test
    fun `migrating from 1 to 2 back-fills the last sync timestamp from the cache timestamp`() =
        runTest {
            createVersion1Database()

            val row = requireNotNull(openWithMigrations().apodDao().findByDate("2026-09-10"))

            assertEquals(CACHED_AT_MILLIS, row.lastSyncedAtEpochMillis)
        }

    @Test
    fun `migrating keeps every pre-existing row in the feed`() = runTest {
        createVersion1Database()

        val dao = openWithMigrations().apodDao()

        // Every row predating the calendar arrived through a window sync, so none of them may
        // silently vanish from the feed on upgrade.
        assertEquals(2, dao.observeFeed().first().size)
    }

    @Test
    fun `a synced row keeps its original cache timestamp and advances only the sync timestamp`() =
        runTest {
            createVersion1Database()
            val dao = openWithMigrations().apodDao()

            dao.upsertPreservingLocalState(
                listOf(
                    requireNotNull(dao.findByDate("2026-09-10")).copy(
                        title = "Re-synced title",
                        cachedAtEpochMillis = RESYNCED_AT_MILLIS,
                        lastSyncedAtEpochMillis = RESYNCED_AT_MILLIS,
                    ),
                ),
            )

            val row = requireNotNull(dao.findByDate("2026-09-10"))
            assertEquals("Re-synced title", row.title)
            assertEquals(CACHED_AT_MILLIS, row.cachedAtEpochMillis)
            assertEquals(RESYNCED_AT_MILLIS, row.lastSyncedAtEpochMillis)
            assertTrue(row.isFavorite)
        }

    private fun openWithMigrations(): NasaDatabase =
        Room.databaseBuilder(context, NasaDatabase::class.java, TEST_DB_NAME)
            .addMigrations(*NasaDatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

    /**
     * Writes the schema exactly as version 1 shipped it (see `schemas/…/1.json`), including the
     * Room identity row, so Room treats the file as a genuine v1 database and upgrades it.
     */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(VERSION_1_CREATE_APODS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apods_is_favorite` ON `apods` (`is_favorite`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apods_media_type` ON `apods` (`media_type`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_1_IDENTITY_HASH),
            )

            db.execSQL(
                INSERT_APOD,
                arrayOf<Any>(
                    "2026-09-10",
                    "Spiral Galaxy NGC 1300",
                    "A barred spiral galaxy.",
                    "image",
                    "https://apod.nasa.gov/apod/image/ngc1300.jpg",
                    1,
                    CACHED_AT_MILLIS,
                ),
            )
            db.execSQL(
                INSERT_APOD,
                arrayOf<Any>(
                    "2026-09-09",
                    "Flight Through the Orion Nebula",
                    "A visualization of the Orion Nebula.",
                    "video",
                    "https://www.youtube.com/embed/abc123",
                    0,
                    CACHED_AT_MILLIS,
                ),
            )

            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "migration-test.db"
        const val VERSION_1_IDENTITY_HASH = "507d38c1f18bed85a7a454240f1a6b55"
        const val CACHED_AT_MILLIS = 1_757_500_000_000L
        const val RESYNCED_AT_MILLIS = 1_760_000_000_000L

        const val VERSION_1_CREATE_APODS = "CREATE TABLE IF NOT EXISTS `apods` (" +
            "`date` TEXT NOT NULL, `title` TEXT NOT NULL, `explanation` TEXT NOT NULL, " +
            "`media_type` TEXT NOT NULL, `url` TEXT NOT NULL, `hd_url` TEXT, " +
            "`thumbnail_url` TEXT, `copyright` TEXT, " +
            "`is_favorite` INTEGER NOT NULL DEFAULT 0, " +
            "`cached_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`date`))"

        const val INSERT_APOD = "INSERT INTO `apods` " +
            "(`date`, `title`, `explanation`, `media_type`, `url`, `is_favorite`, " +
            "`cached_at_epoch_millis`) VALUES (?, ?, ?, ?, ?, ?, ?)"
    }
}
