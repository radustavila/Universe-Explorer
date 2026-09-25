package com.radustavila.nasaexplorer.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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

    /** Every migration the database ships with, in order. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
