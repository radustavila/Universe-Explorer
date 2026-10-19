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

    /** Every migration the database ships with, in order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

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
