package com.radustavila.nasaexplorer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.radustavila.nasaexplorer.core.database.dao.ApodDao
import com.radustavila.nasaexplorer.core.database.dao.EpicDao
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.ApodEntity
import com.radustavila.nasaexplorer.core.database.entity.ApodFtsEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicAvailableDateEntity
import com.radustavila.nasaexplorer.core.database.entity.EpicImageEntity
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity

@Database(
    entities = [
        ApodEntity::class,
        ApodFtsEntity::class,
        FavoriteEntity::class,
        EpicImageEntity::class,
        EpicAvailableDateEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class NasaDatabase : RoomDatabase() {
    abstract fun apodDao(): ApodDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun epicDao(): EpicDao

    companion object {
        const val NAME = "nasa-explorer.db"
    }
}
