package com.radustavila.nasaexplorer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity

@Database(
    entities = [ApodEntity::class, FavoriteEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class NasaDatabase : RoomDatabase() {
    abstract fun apodDao(): ApodDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        const val NAME = "nasa-explorer.db"
    }
}
