package com.radustavila.nasaexplorer.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicAvailableDateEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.database.migration.NasaDatabaseMigrations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `a favourite from version 1 ends up in the favourite table`() = runTest {
        createVersion1Database()

        val favorites = openWithMigrations().favoriteDao().observeAll().first()

        assertEquals(listOf("2026-09-10"), favorites.map(FavoriteEntity::itemId))
        assertEquals("apod", favorites.single().source)
    }

    @Test
    fun `migrating from 3 to 4 keeps every existing APOD favourite`() = runTest {
        createVersion3Database()

        val database = openWithMigrations()
        val favorites = database.favoriteDao().observeAll().first().associateBy(FavoriteEntity::itemId)

        assertEquals(setOf("2026-09-10", "2026-09-09", "2026-09-08", "2001-03-05"), favorites.keys)
        assertTrue(favorites.values.all { it.source == "apod" })
        assertEquals("Spiral Galaxy NGC 1300", favorites.getValue("2026-09-10").title)
        assertEquals(CACHED_AT_MILLIS, favorites.getValue("2026-09-10").savedAtEpochMillis)
        // The snapshot image is the entry's preview, whatever its media type.
        assertEquals("https://apod.nasa.gov/apod/image/ngc1300.jpg", favorites.getValue("2026-09-10").imageUrl)
        assertEquals(
            "https://img.youtube.com/vi/abcdefghijk/hqdefault.jpg",
            favorites.getValue("2026-09-09").imageUrl,
        )
        assertEquals("https://apod.nasa.gov/apod/image/aurora.mp4", favorites.getValue("2026-09-08").imageUrl)
        assertEquals("https://apod.nasa.gov/apod/image/old_thumb.jpg", favorites.getValue("2001-03-05").imageUrl)

        // Nothing else moved and no cached row was lost.
        assertEquals(5, database.apodDao().observeCount().first())
        assertEquals("2001-03-05", database.apodDao().findByDate("2001-03-05")?.date)
    }

    @Test
    fun `migrating from 3 through 4 to 5 keeps rows and favourites, with the new columns empty`() = runTest {
        createVersion3Database()

        val database = openWithMigrations()
        val dao = database.apodDao()

        assertEquals(5, dao.observeCount().first())
        val row = requireNotNull(dao.findByDate("2026-09-10"))
        assertEquals("Spiral Galaxy NGC 1300", row.title)
        assertNull(row.permalink)
        assertNull(row.alt)
        assertNull(row.explanationLinks)
        assertFalse(requireNotNull(dao.findByDate("2001-03-05")).isInFeed)
        // F0's 3 -> 4 step still moved the favourites before F1's 4 -> 5 step ran.
        assertEquals(
            setOf("2026-09-10", "2026-09-09", "2026-09-08", "2001-03-05"),
            database.favoriteDao().observeAll().first().map(FavoriteEntity::itemId).toSet(),
        )
    }

    @Test
    fun `migrating to 5 indexes the rows already cached for search`() = runTest {
        createVersion3Database()

        val dao = openWithMigrations().apodDao()

        assertEquals(
            listOf("2026-09-10"),
            dao.observeSearch(match = "spiral*", limit = 10).first().map(ApodEntity::date),
        )
        // A day opened from the calendar is searchable too, though it is not in the feed.
        assertEquals(
            listOf("2001-03-05"),
            dao.observeSearch(match = "moon*", limit = 10).first().map(ApodEntity::date),
        )
    }

    @Test
    fun `after migrating to 5 the index keeps up with new syncs`() = runTest {
        createVersion3Database()
        val database = openWithMigrations()
        val dao = database.apodDao()

        dao.upsertPreservingLocalState(
            listOf(
                requireNotNull(dao.findByDate("2026-09-10")).copy(
                    title = "Barred Spiral Revisited",
                    permalink = "https://science.nasa.gov/image-article/apod-2026-september-10/",
                ),
            ),
        )

        val row = dao.observeSearch(match = "revisited*", limit = 10).first().single()
        assertEquals("https://science.nasa.gov/image-article/apod-2026-september-10/", row.permalink)
        assertTrue(dao.observeSearch(match = "ngc*", limit = 10).first().isEmpty())
        assertTrue(database.favoriteDao().observeAll().first().any { it.itemId == "2026-09-10" })
    }

    @Test
    fun `migrating from 3 through 4 and 5 to 6 adds an empty EPIC cache and keeps rows and favourites`() =
        runTest {
            createVersion3Database()

            val database = openWithMigrations()

            assertEquals(5, database.apodDao().observeCount().first())
            assertEquals(
                setOf("2026-09-10", "2026-09-09", "2026-09-08", "2001-03-05"),
                database.favoriteDao().observeAll().first().map(FavoriteEntity::itemId).toSet(),
            )
            val epicDao = database.epicDao()
            assertTrue(epicDao.observeLatestDay("natural").first().isEmpty())
            assertTrue(epicDao.observeAvailableDates("natural").first().isEmpty())

            // The new tables take writes straight away.
            epicDao.replaceAvailableDates("natural", listOf(EpicAvailableDateEntity("natural", "2026-10-16")))
            assertEquals(listOf("2026-10-16"), epicDao.observeAvailableDates("natural").first())
        }

    @Test
    fun `migrating from 6 to 7 keeps every favourite, none of them with an offline copy yet`() = runTest {
        createDatabaseFromExportedSchema(version = 6)
        SQLiteDatabase.openDatabase(context.getDatabasePath(TEST_DB_NAME).path, null, 0).use { db ->
            db.execSQL(
                "INSERT INTO `favorite` (`source`, `item_id`, `title`, `image_url`, `saved_at_epoch_millis`) " +
                    "VALUES ('apod', '2026-09-10', 'Spiral Galaxy NGC 1300', ?, ?), " +
                    "('epic', 'natural/2026-10-16/epic_1b', 'Earth', ?, ?), " +
                    "('exoplanet', 'Kepler-22 b', 'Kepler-22 b', NULL, ?)",
                arrayOf<Any>(
                    "https://apod.nasa.gov/apod/image/ngc1300.jpg", CACHED_AT_MILLIS,
                    "https://epic.gsfc.nasa.gov/epic_1b.jpg", CACHED_AT_MILLIS + 1,
                    CACHED_AT_MILLIS + 2,
                ),
            )
        }

        val dao = openWithMigrations().favoriteDao()

        val favorites = dao.observeAll().first()
        assertEquals(listOf("Kepler-22 b", "natural/2026-10-16/epic_1b", "2026-09-10"), favorites.map { it.itemId })
        assertEquals("https://apod.nasa.gov/apod/image/ngc1300.jpg", favorites.last().imageUrl)
        assertTrue(favorites.all { it.localImagePath == null && it.localImageBytes == 0L })
        // Exactly the favourites with a picture are waiting for the one-off backfill.
        assertEquals(
            setOf("2026-09-10", "natural/2026-10-16/epic_1b"),
            dao.findWithoutLocalImage().map { it.itemId }.toSet(),
        )
        assertEquals(0L, dao.observeLocalImageBytes().first())

        // The new columns take writes straight away.
        dao.setLocalImage(
            source = "apod",
            itemId = "2026-09-10",
            imageUrl = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
            localImagePath = "apod/2026-09-10.jpg",
            localImageBytes = 2048,
        )
        assertEquals("apod/2026-09-10.jpg", dao.find("apod", "2026-09-10")?.localImagePath)
        assertEquals(2048L, dao.observeLocalImageBytes().first())
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

    /** Writes a version-3 database (see `schemas/…/3.json`) with favourites of every media type. */
    private fun createVersion3Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(VERSION_3_CREATE_APODS)
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apods_is_favorite` ON `apods` (`is_favorite`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apods_is_in_feed` ON `apods` (`is_in_feed`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_apods_media_type` ON `apods` (`media_type`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_3_IDENTITY_HASH),
            )

            listOf(
                arrayOf<Any?>(
                    "2026-09-10", "Spiral Galaxy NGC 1300", "image",
                    "https://apod.nasa.gov/apod/image/ngc1300.jpg", null, 1, 1,
                ),
                arrayOf<Any?>(
                    "2026-09-09", "Flight Through the Orion Nebula", "video",
                    "https://www.youtube.com/embed/abcdefghijk", null, 1, 1,
                ),
                arrayOf<Any?>(
                    "2026-09-08", "Aurora Timelapse", "video",
                    "https://apod.nasa.gov/apod/image/aurora.mp4", null, 1, 1,
                ),
                arrayOf<Any?>("2026-09-07", "Not a favourite", "image", "https://apod.nasa.gov/x.jpg", null, 0, 1),
                // Opened from the calendar and favourited: outside the feed window.
                arrayOf<Any?>(
                    "2001-03-05", "Interactive Moon", "other", "https://apod.nasa.gov/moon.html",
                    "https://apod.nasa.gov/apod/image/old_thumb.jpg", 1, 0,
                ),
            ).forEach { row ->
                db.execSQL(
                    "INSERT INTO `apods` (`date`, `title`, `explanation`, `media_type`, `url`, " +
                        "`thumbnail_url`, `is_favorite`, `is_in_feed`, `cached_at_epoch_millis`, " +
                        "`last_synced_at_epoch_millis`) VALUES (?, ?, '', ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(*row, CACHED_AT_MILLIS, CACHED_AT_MILLIS),
                )
            }

            db.version = 3
        } finally {
            db.close()
        }
    }

    /**
     * Writes an empty database exactly as [version] shipped it, replaying the statements Room
     * exported to `schemas/…/<version>.json` (tables, indices, FTS triggers and the identity row).
     */
    private fun createDatabaseFromExportedSchema(version: Int) {
        val schema = JSONObject(File(SCHEMA_DIRECTORY, "$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (i in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                entity.optJSONArray("contentSyncTriggers")?.let { triggers ->
                    for (i in 0 until triggers.length()) db.execSQL(triggers.getString(i))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
            db.version = version
        }
    }

    private companion object {
        /** Room's exported schemas, relative to the module directory tests run in. */
        const val SCHEMA_DIRECTORY = "schemas/com.radustavila.nasaexplorer.core.database.NasaDatabase"
        const val TEST_DB_NAME = "migration-test.db"
        const val VERSION_1_IDENTITY_HASH = "507d38c1f18bed85a7a454240f1a6b55"
        const val CACHED_AT_MILLIS = 1_757_500_000_000L
        const val RESYNCED_AT_MILLIS = 1_760_000_000_000L

        const val VERSION_3_IDENTITY_HASH = "543db70f10105a589d2b8b48f97d9a0c"

        const val VERSION_3_CREATE_APODS = "CREATE TABLE IF NOT EXISTS `apods` (" +
            "`date` TEXT NOT NULL, `title` TEXT NOT NULL, `explanation` TEXT NOT NULL, " +
            "`media_type` TEXT NOT NULL, `url` TEXT NOT NULL, `hd_url` TEXT, " +
            "`thumbnail_url` TEXT, `copyright` TEXT, `is_favorite` INTEGER NOT NULL DEFAULT 0, " +
            "`cached_at_epoch_millis` INTEGER NOT NULL, " +
            "`last_synced_at_epoch_millis` INTEGER NOT NULL DEFAULT 0, " +
            "`is_in_feed` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`date`))"

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
