package com.radustavila.nasaexplorer.data.nasaimages.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The library cache lives in its own small database rather than in `NasaDatabase`: it is a mirror
 * of a public catalogue whose only user data is which items are saved (and that list lives in the
 * `favorite` table), so a schema change may simply rebuild it, and it never competes with the APOD
 * schema's versions.
 */
@Database(
    entities = [NasaMediaEntity::class, RailItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NasaImagesDatabase : RoomDatabase() {
    abstract fun nasaMediaDao(): NasaMediaDao

    companion object {
        const val NAME = "nasa_images.db"
    }
}
