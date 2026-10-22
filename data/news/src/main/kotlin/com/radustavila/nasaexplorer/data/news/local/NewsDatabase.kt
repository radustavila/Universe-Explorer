package com.radustavila.nasaexplorer.data.news.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The news cache in its own small database, like the other feeds' mirrors: no user data (favourites
 * live in `NasaDatabase`), so a schema change may simply rebuild it.
 */
@Database(
    entities = [NewsItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao

    companion object {
        const val NAME = "news.db"
    }
}
