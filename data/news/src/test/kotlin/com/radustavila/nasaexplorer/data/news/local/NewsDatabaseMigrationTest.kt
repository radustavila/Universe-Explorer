package com.radustavila.nasaexplorer.data.news.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.testing.data.TestPodcasts
import com.radustavila.nasaexplorer.data.news.mapper.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Version 2 (podcasts, F21) migrates automatically: cached news stays, and the podcast tables work.
 * Opening through Room also checks the migrated schema against the entities.
 */
@RunWith(RobolectricTestRunner::class)
class NewsDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: NewsDatabase? = null

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
    fun `migrating from 1 to 2 keeps the cached news and adds the podcast tables`() = runTest {
        createVersion1Database()

        val migrated = Room.databaseBuilder(context, NewsDatabase::class.java, TEST_DB_NAME)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        assertEquals(listOf(STORY_ID), migrated.newsDao().observeAll().first().map { it.id })
        val episode = TestPodcasts.houston()
        val dao = migrated.podcastDao()
        dao.replaceShow(episode.show.key, listOf(episode.toEntity(TestPodcasts.NOW)), keepIds = emptyList())
        dao.upsertProgress(PodcastProgressEntity(episode.id, positionMillis = 60_000, isFinished = false, 0))
        dao.upsertDownload(PodcastDownloadEntity(episode.id, PodcastDownloadState.QUEUED.name, null, 0, 0))
        val row = dao.observeById(episode.id).first()!!
        assertEquals(60_000L, row.progress?.positionMillis)
        assertEquals(PodcastDownloadState.QUEUED.name, row.download?.state)
    }

    /** The schema exactly as version 1 shipped it (`schemas/…/1.json`), with Room's identity row. */
    private fun createVersion1Database() {
        val file = context.getDatabasePath(TEST_DB_NAME)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `news_item` (`feed` TEXT NOT NULL, `id` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `published_at` INTEGER NOT NULL, `summary` TEXT, " +
                    "`image_url` TEXT, `full_image_url` TEXT, `image_credit` TEXT, `is_live` INTEGER NOT NULL, " +
                    "`cached_at` INTEGER NOT NULL, PRIMARY KEY(`feed`, `id`))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_item_published_at` ON `news_item` (`published_at`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_news_item_id` ON `news_item` (`id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, ?)",
                arrayOf(VERSION_1_IDENTITY_HASH),
            )
            db.execSQL(
                "INSERT INTO news_item VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "stories", STORY_ID, "Busy Station Day", STORY_ID, 1_792_430_000_000L, null, null, null, null, 0,
                    1_792_430_000_000L,
                ),
            )
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val TEST_DB_NAME = "news-migration-test.db"
        const val VERSION_1_IDENTITY_HASH = "805d3a15d7b9f7656db5810f58d0e30d"
        const val STORY_ID = "https://www.nasa.gov/blogs/spacestation/2026/10/22/busy-station-day/"
    }
}
