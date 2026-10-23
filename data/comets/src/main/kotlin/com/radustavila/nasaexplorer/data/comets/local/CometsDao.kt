package com.radustavila.nasaexplorer.data.comets.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The module's one DAO, over its two tables: reads, whole-refresh writes and the evictor's trims. */
@Suppress("TooManyFunctions")
@Dao
interface CometsDao {

    @Transaction
    @Query("SELECT * FROM comet")
    fun observeComets(): Flow<List<CometWithEphemeris>>

    @Transaction
    @Query("SELECT * FROM comet WHERE designation = :designation")
    fun observeComet(designation: String): Flow<CometWithEphemeris?>

    /**
     * Stores a refresh: [comets] become the whole candidate list (comets SBDB no longer lists go,
     * with their points), and each comet in [ephemerides] gets its points replaced. Comets without
     * a fresh ephemeris keep the points they had.
     */
    @Transaction
    suspend fun storeRefresh(comets: List<CometEntity>, ephemerides: Map<String, List<CometEphemerisEntity>>) {
        deleteCometsOtherThan(comets.map { it.designation })
        upsertComets(comets)
        ephemerides.forEach { (designation, points) ->
            deleteEphemeris(designation)
            insertEphemeris(points)
        }
        deleteOrphanedEphemeris()
    }

    @Query("SELECT MAX(fetched_at) FROM comet_ephemeris")
    suspend fun newestEphemerisFetchedAt(): Long?

    @Query("DELETE FROM comet_ephemeris WHERE time < :cutoffMillis")
    suspend fun deleteEphemerisBefore(cutoffMillis: Long): Int

    /** Empties both tables, for "Clear cache": all of it can be downloaded again. */
    @Transaction
    suspend fun clearAll() {
        clearEphemeris()
        clearComets()
    }

    @Upsert
    suspend fun upsertComets(comets: List<CometEntity>)

    @Insert
    suspend fun insertEphemeris(points: List<CometEphemerisEntity>)

    @Query("DELETE FROM comet WHERE designation NOT IN (:designations)")
    suspend fun deleteCometsOtherThan(designations: List<String>)

    @Query("DELETE FROM comet_ephemeris WHERE designation = :designation")
    suspend fun deleteEphemeris(designation: String)

    @Query("DELETE FROM comet_ephemeris WHERE designation NOT IN (SELECT designation FROM comet)")
    suspend fun deleteOrphanedEphemeris()

    @Query("DELETE FROM comet")
    suspend fun clearComets()

    @Query("DELETE FROM comet_ephemeris")
    suspend fun clearEphemeris()
}
