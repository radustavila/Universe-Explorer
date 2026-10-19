package com.radustavila.nasaexplorer.data.sky.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The latest element set of one satellite, as CelesTrak published it. */
@Entity(tableName = "orbital_elements")
data class OrbitalElementsEntity(
    @PrimaryKey @ColumnInfo(name = "norad_id") val noradId: Int,
    val name: String,
    @ColumnInfo(name = "epoch_millis") val epochMillis: Long,
    @ColumnInfo(name = "mean_motion") val meanMotion: Double,
    val eccentricity: Double,
    @ColumnInfo(name = "inclination_degrees") val inclinationDegrees: Double,
    @ColumnInfo(name = "raan_degrees") val raanDegrees: Double,
    @ColumnInfo(name = "arg_of_pericenter_degrees") val argOfPericenterDegrees: Double,
    @ColumnInfo(name = "mean_anomaly_degrees") val meanAnomalyDegrees: Double,
    val bstar: Double,
    @ColumnInfo(name = "fetched_at_millis") val fetchedAtMillis: Long,
)

@Dao
interface OrbitalElementsDao {

    @Query("SELECT * FROM orbital_elements WHERE norad_id = :noradId")
    fun observe(noradId: Int): Flow<OrbitalElementsEntity?>

    @Upsert
    suspend fun upsert(elements: List<OrbitalElementsEntity>)

    /** @return how many element sets were removed. */
    @Query("DELETE FROM orbital_elements WHERE epoch_millis < :epochBeforeMillis")
    suspend fun deleteWithEpochBefore(epochBeforeMillis: Long): Int
}

/**
 * Sky tonight's own database: the orbital elements satellite predictions are computed from. Like
 * the other feature caches it is a disposable mirror (a schema change may rebuild it; one refresh
 * fills it again), kept apart from `NasaDatabase` and its migrations.
 */
@Database(entities = [OrbitalElementsEntity::class], version = 1, exportSchema = true)
abstract class SkyDatabase : RoomDatabase() {
    abstract fun orbitalElementsDao(): OrbitalElementsDao

    companion object {
        const val NAME = "sky.db"
    }
}
