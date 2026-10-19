package com.radustavila.nasaexplorer.data.mars.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The rover photo cache, in its own small database rather than in `NasaDatabase`: it mirrors a
 * public feed and holds no user data (favourites live in the `favorite` table), so a schema change
 * may simply rebuild it, and it never competes with the APOD schema's versions.
 */
@Database(
    entities = [MarsPhotoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MarsDatabase : RoomDatabase() {
    abstract fun marsPhotoDao(): MarsPhotoDao

    companion object {
        const val NAME = "mars.db"
    }
}
