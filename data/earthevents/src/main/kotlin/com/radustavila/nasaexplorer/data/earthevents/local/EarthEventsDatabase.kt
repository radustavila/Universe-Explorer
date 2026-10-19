package com.radustavila.nasaexplorer.data.earthevents.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The events cache lives in its own small database rather than in `NasaDatabase`: it is a mirror
 * of a public feed with no user data at all, so a schema change may simply rebuild it (one
 * refresh), and it never competes with the APOD schema's versions.
 */
@Database(
    entities = [EarthEventEntity::class, ImageryLayerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EarthEventsDatabase : RoomDatabase() {
    abstract fun earthEventDao(): EarthEventDao

    companion object {
        const val NAME = "earth_events.db"
    }
}
