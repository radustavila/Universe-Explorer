package com.radustavila.nasaexplorer.data.exoplanets.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExoplanetDao {

    /** Newest publication first; planets without a publication month go last. */
    @Query(
        """
        SELECT * FROM exoplanets
        WHERE disc_year >= :sinceYear
        ORDER BY disc_pubdate IS NULL, disc_pubdate DESC, disc_year DESC, name
        """,
    )
    fun observeNewest(sinceYear: Int): Flow<List<ExoplanetEntity>>

    @Query(
        """
        SELECT * FROM exoplanets
        WHERE distance_pc IS NOT NULL
        ORDER BY distance_pc, name
        LIMIT :limit
        """,
    )
    fun observeNearest(limit: Int): Flow<List<ExoplanetEntity>>

    /** Nearest first; planets of unknown distance last. */
    @Query(
        """
        SELECT * FROM exoplanets
        WHERE radius_earth BETWEEN :minRadius AND :maxRadius
        ORDER BY distance_pc IS NULL, distance_pc, name
        """,
    )
    fun observeByRadius(minRadius: Double, maxRadius: Double): Flow<List<ExoplanetEntity>>

    @Query("SELECT * FROM exoplanets WHERE name = :name")
    fun observe(name: String): Flow<ExoplanetEntity?>

    @Query("SELECT * FROM exoplanet_catalog_stats WHERE id = ${ExoplanetCatalogStatsEntity.SINGLE_ROW_ID}")
    fun observeStats(): Flow<ExoplanetCatalogStatsEntity?>

    @Query("SELECT MAX(fetched_at) FROM exoplanets")
    suspend fun newestFetchedAt(): Long?

    @Query("SELECT COUNT(*) FROM exoplanets")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(planets: List<ExoplanetEntity>)

    @Upsert
    suspend fun upsertStats(stats: ExoplanetCatalogStatsEntity)

    /** Stores one refresh: its planets and, when the count query succeeded, the archive total. */
    @Transaction
    suspend fun storeRefresh(planets: List<ExoplanetEntity>, stats: ExoplanetCatalogStatsEntity?) {
        upsertAll(planets)
        if (stats != null) upsertStats(stats)
    }

    /** Deletes planets last returned before [cutoffMillis], except the [keep] names (favourites). */
    @Query("DELETE FROM exoplanets WHERE fetched_at < :cutoffMillis AND name NOT IN (:keep)")
    suspend fun deleteFetchedBefore(cutoffMillis: Long, keep: List<String>): Int
}
