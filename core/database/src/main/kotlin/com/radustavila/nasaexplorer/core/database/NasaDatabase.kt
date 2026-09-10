package com.radustavila.nasaexplorer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity

@Database(
    entities = [ApodEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NasaDatabase : RoomDatabase() {
    abstract fun apodDao(): ApodDao

    companion object {
        const val NAME = "nasa-explorer.db"
    }
}
