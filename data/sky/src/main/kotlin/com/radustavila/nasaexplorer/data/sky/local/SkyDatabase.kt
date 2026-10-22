package com.radustavila.nasaexplorer.data.sky.local

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
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
    /** International designator, e.g. `2026-232A` (schema 2). */
    @ColumnInfo(name = "object_id") val objectId: String? = null,
    @ColumnInfo(name = "rev_at_epoch") val revAtEpoch: Long? = null,
)

/**
 * Which objects a CelesTrak group (the bright satellites, the recent Starlinks) held at its last
 * refresh. Single named objects (the ISS, Hubble, Tiangong) are read by catalogue number instead.
 */
@Entity(tableName = "satellite_set_member", primaryKeys = ["set_name", "norad_id"])
data class SatelliteSetMemberEntity(
    /** `SatelliteSet.name`. */
    @ColumnInfo(name = "set_name") val setName: String,
    @ColumnInfo(name = "norad_id") val noradId: Int,
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

    @Query(
        "SELECT e.* FROM orbital_elements e JOIN satellite_set_member m ON m.norad_id = e.norad_id " +
            "WHERE m.set_name = :setName ORDER BY e.norad_id",
    )
    fun observeSet(setName: String): Flow<List<OrbitalElementsEntity>>

    @Query("DELETE FROM satellite_set_member WHERE set_name = :setName")
    suspend fun clearSet(setName: String)

    @Insert
    suspend fun insertMembers(members: List<SatelliteSetMemberEntity>)

    /** Element sets no group holds any more, except the named objects in [keep]. */
    @Query(
        "DELETE FROM orbital_elements WHERE norad_id NOT IN (SELECT norad_id FROM satellite_set_member) " +
            "AND norad_id NOT IN (:keep)",
    )
    suspend fun deleteUnreferenced(keep: List<Int>)

    /** Makes [elements] the whole of group [setName], in one transaction. */
    @Transaction
    suspend fun replaceSet(setName: String, elements: List<OrbitalElementsEntity>, keep: List<Int>) {
        upsert(elements)
        clearSet(setName)
        insertMembers(elements.map { SatelliteSetMemberEntity(setName = setName, noradId = it.noradId) })
        deleteUnreferenced(keep)
    }
}

/**
 * Sky tonight's own database: the orbital elements satellite predictions are computed from. Like
 * the other feature caches it is a disposable mirror, kept apart from `NasaDatabase` and its
 * migrations. Version 2 (more satellites) only adds, so it migrates automatically and the ISS keeps
 * its elements: rebuilding would leave it without any until its next due refresh, up to 12 hours.
 */
@Database(
    entities = [OrbitalElementsEntity::class, SatelliteSetMemberEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SkyDatabase : RoomDatabase() {
    abstract fun orbitalElementsDao(): OrbitalElementsDao

    companion object {
        const val NAME = "sky.db"
    }
}
