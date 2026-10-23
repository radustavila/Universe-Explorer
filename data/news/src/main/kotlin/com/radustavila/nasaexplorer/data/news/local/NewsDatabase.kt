package com.radustavila.nasaexplorer.data.news.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The news cache in its own small database, like the other feeds' mirrors. Favourites live in
 * `NasaDatabase`, but version 2 (podcasts, F21) adds listening progress and downloads, which are the
 * user's own: schema changes migrate from then on instead of rebuilding.
 */
@Database(
    entities = [
        NewsItemEntity::class,
        PodcastEpisodeEntity::class,
        PodcastProgressEntity::class,
        PodcastDownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao

    abstract fun podcastDao(): PodcastDao

    companion object {
        const val NAME = "news.db"
    }
}
