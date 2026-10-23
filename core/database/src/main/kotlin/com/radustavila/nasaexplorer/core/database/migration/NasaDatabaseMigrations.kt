package com.radustavila.nasaexplorer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.model.MediaType
import java.time.LocalDate

/**
 * Schema history of [com.radustavila.nasaexplorer.core.database.NasaDatabase].
 *
 * The cache is expensive to rebuild - it is the only thing the app can show offline, and it carries
 * local state (favourites) that exists nowhere else - so every version bump gets a real migration
 * instead of a destructive fallback.
 */
object NasaDatabaseMigrations {

    /**
     * v1 -> v2: splits "when was this row cached" into two facts.
     *
     * `cached_at_epoch_millis` keeps its original meaning (first time the entry entered the cache)
     * and is no longer touched by a sync; the new `last_synced_at_epoch_millis` records the last
     * time the entry was confirmed against the API, which is what staleness is actually measured
     * against.
     *
     * Existing rows are back-filled from `cached_at_epoch_millis`: at the moment of the upgrade the
     * two timestamps are, by definition, the same.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE `apods` ADD COLUMN `last_synced_at_epoch_millis` " +
                    "INTEGER NOT NULL DEFAULT 0",
            )
            connection.execSQL(
                "UPDATE `apods` SET `last_synced_at_epoch_millis` = `cached_at_epoch_millis`",
            )
        }
    }

    /**
     * v2 -> v3: separates the feed's contiguous date window from one-off days.
     *
     * Opening a day from the calendar caches it as an isolated row, which the feed must not show
     * between entries from a completely different part of the archive.
     *
     * Every pre-existing row arrived through a window sync, so all of them are in the feed - which
     * is exactly what the column default gives them.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE `apods` ADD COLUMN `is_in_feed` INTEGER NOT NULL DEFAULT 1",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_apods_is_in_feed` ON `apods` (`is_in_feed`)",
            )
        }
    }

    /**
     * v3 -> v4: favourites of every source move to their own `favorite` table, keyed by
     * `(source, item_id)` with a snapshot of title and image.
     *
     * Every APOD row flagged `is_favorite` is copied over as an [FavoriteSource.APOD] favourite. The
     * snapshot image is the entry's preview, computed by [Apod] itself so a migrated video favourite
     * gets the same still frame as one saved after the upgrade. The original cache time stands in
     * for the save time, which was never recorded.
     *
     * The old column stays (dropping it would mean rebuilding `apods`), but its index goes.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite` (`source` TEXT NOT NULL, " +
                    "`item_id` TEXT NOT NULL, `title` TEXT NOT NULL, `image_url` TEXT, " +
                    "`saved_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`source`, `item_id`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_favorite_saved_at_epoch_millis` " +
                    "ON `favorite` (`saved_at_epoch_millis`)",
            )

            val favorites = connection.readApodFavorites()
            connection.prepare(
                "INSERT OR IGNORE INTO `favorite` " +
                    "(`source`, `item_id`, `title`, `image_url`, `saved_at_epoch_millis`) " +
                    "VALUES (?, ?, ?, ?, ?)",
            ).use { insert ->
                favorites.forEach { favorite ->
                    insert.bindText(1, FavoriteSource.APOD.key)
                    insert.bindText(2, favorite.itemId)
                    insert.bindText(3, favorite.title)
                    favorite.imageUrl?.let { insert.bindText(4, it) } ?: insert.bindNull(4)
                    insert.bindLong(5, favorite.savedAtEpochMillis)
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }

            connection.execSQL("DROP INDEX IF EXISTS `index_apods_is_favorite`")
        }
    }

    /**
     * v4 -> v5: stores what the science.nasa.gov feed offers beyond the old API, and indexes the
     * cache for full-text search.
     *
     * - `permalink`, `alt` and `explanation_links` are nullable: existing rows keep working (the
     *   model falls back to the archive URL and the title) and pick the values up on their next sync.
     * - `apods_fts` is an external-content FTS4 table over `title` and `explanation`, kept in step by
     *   the same triggers Room creates for a fresh install. `rebuild` indexes every row already
     *   cached, so search covers the whole cache straight after the upgrade.
     *
     * The statements mirror `schemas/…/5.json` verbatim; opening the migrated database through Room
     * validates them against it.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `apods` ADD COLUMN `permalink` TEXT")
            connection.execSQL("ALTER TABLE `apods` ADD COLUMN `alt` TEXT")
            connection.execSQL("ALTER TABLE `apods` ADD COLUMN `explanation_links` TEXT")

            connection.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `apods_fts` USING FTS4(" +
                    "`title` TEXT NOT NULL, `explanation` TEXT NOT NULL, " +
                    "tokenize=porter, content=`apods`)",
            )
            FTS_CONTENT_SYNC_TRIGGERS.forEach(connection::execSQL)
            connection.execSQL("INSERT INTO `apods_fts`(`apods_fts`) VALUES ('rebuild')")
        }
    }

    private val FTS_CONTENT_SYNC_TRIGGERS = listOf(
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_apods_fts_BEFORE_UPDATE " +
            "BEFORE UPDATE ON `apods` BEGIN DELETE FROM `apods_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_apods_fts_BEFORE_DELETE " +
            "BEFORE DELETE ON `apods` BEGIN DELETE FROM `apods_fts` WHERE `docid`=OLD.`rowid`; END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_apods_fts_AFTER_UPDATE " +
            "AFTER UPDATE ON `apods` BEGIN INSERT INTO `apods_fts`(`docid`, `title`, `explanation`) " +
            "VALUES (NEW.`rowid`, NEW.`title`, NEW.`explanation`); END",
        "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_apods_fts_AFTER_INSERT " +
            "AFTER INSERT ON `apods` BEGIN INSERT INTO `apods_fts`(`docid`, `title`, `explanation`) " +
            "VALUES (NEW.`rowid`, NEW.`title`, NEW.`explanation`); END",
    )

    /**
     * v5 -> v6: adds the EPIC (DSCOVR whole-Earth photo) cache.
     *
     * Two new tables and nothing else: existing tables are untouched, and both new tables start
     * empty because the first EPIC sync fills them.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `epic_images` (" +
                    "`collection` TEXT NOT NULL, `identifier` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "`captured_at_epoch_millis` INTEGER NOT NULL, `caption` TEXT NOT NULL, " +
                    "`centroid_latitude` REAL NOT NULL, `centroid_longitude` REAL NOT NULL, " +
                    "`thumbnail_url` TEXT NOT NULL, `image_url` TEXT NOT NULL, " +
                    "`cached_at_epoch_millis` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`collection`, `identifier`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_epic_images_collection_date` " +
                    "ON `epic_images` (`collection`, `date`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `epic_available_dates` (" +
                    "`collection` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                    "PRIMARY KEY(`collection`, `date`))",
            )
        }
    }

    /**
     * v6 -> v7: favourites can keep an offline copy of their snapshot image.
     *
     * Both columns start empty: no copy exists yet. The app backfills copies for every existing
     * favourite once, in the background, after the upgrade.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `favorite` ADD COLUMN `local_image_path` TEXT")
            connection.execSQL(
                "ALTER TABLE `favorite` ADD COLUMN `local_image_bytes` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    /**
     * v7 -> v8: adds the observing log (F23), one "seen" mark per deep-sky object. A new, empty table;
     * nothing else changes.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `observing_log` (`object_id` TEXT NOT NULL, " +
                    "`observed_on` TEXT NOT NULL, `conditions` TEXT, `note` TEXT, " +
                    "`logged_at_epoch_millis` INTEGER NOT NULL, PRIMARY KEY(`object_id`))",
            )
        }
    }

    /** Every migration the database ships with, in order. */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
    )

    private class MigratedFavorite(
        val itemId: String,
        val title: String,
        val imageUrl: String?,
        val savedAtEpochMillis: Long,
    )

    private fun SQLiteConnection.readApodFavorites(): List<MigratedFavorite> = prepare(
        "SELECT `date`, `title`, `media_type`, `url`, `hd_url`, `thumbnail_url`, " +
            "`cached_at_epoch_millis` FROM `apods` WHERE `is_favorite` = 1",
    ).use { query ->
        buildList {
            while (query.step()) {
                val date = query.getText(0)
                val title = query.getText(1)
                val preview = runCatching {
                    Apod(
                        date = LocalDate.parse(date),
                        title = title,
                        explanation = "",
                        mediaType = MediaType.fromApiValue(query.getText(2)),
                        url = query.getText(3),
                        hdUrl = query.textOrNull(4),
                        thumbnailUrl = query.textOrNull(5),
                    ).previewImageUrl
                }.getOrNull()
                add(MigratedFavorite(date, title, preview, query.getLong(6)))
            }
        }
    }

    private fun androidx.sqlite.SQLiteStatement.textOrNull(index: Int): String? =
        if (isNull(index)) null else getText(index)
}
