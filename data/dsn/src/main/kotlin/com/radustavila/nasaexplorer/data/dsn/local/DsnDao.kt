package com.radustavila.nasaexplorer.data.dsn.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The module's one DAO, over its six small tables: reads, whole-snapshot writes and the evictor's trims. */
@Suppress("TooManyFunctions")
@Dao
interface DsnDao {

    /** The live snapshot in the feed's order; a refresh is seen whole, never half replaced. */
    @Transaction
    @Query("SELECT * FROM dsn_dish ORDER BY site_order, dish_order")
    fun observeLive(): Flow<List<DsnDishWithLinks>>

    @Query("SELECT * FROM dsn_spacecraft")
    fun observeSpacecraft(): Flow<List<DsnSpacecraftEntity>>

    @Query("SELECT * FROM dsn_dish_info")
    fun observeDishInfo(): Flow<List<DsnDishInfoEntity>>

    @Query("SELECT * FROM probe_distance")
    fun observeProbeDistances(): Flow<List<ProbeDistanceEntity>>

    /** Swaps the stored snapshot for [dishes] and their links and signals. */
    @Transaction
    suspend fun replaceLive(dishes: List<DsnDishEntity>, links: List<DsnLinkEntity>, signals: List<DsnSignalEntity>) {
        clearLive()
        insertDishes(dishes)
        insertLinks(links)
        insertSignals(signals)
    }

    /** Stores a fresh `config.xml`: names it no longer lists stay until the evictor trims them. */
    @Transaction
    suspend fun storeConfig(spacecraft: List<DsnSpacecraftEntity>, dishes: List<DsnDishInfoEntity>) {
        upsertSpacecraft(spacecraft)
        upsertDishInfo(dishes)
    }

    @Upsert
    suspend fun upsertProbeDistances(distances: List<ProbeDistanceEntity>)

    @Query("SELECT MAX(fetched_at) FROM dsn_dish")
    suspend fun newestLiveFetchedAt(): Long?

    @Query("SELECT MAX(fetched_at) FROM dsn_spacecraft")
    suspend fun newestConfigFetchedAt(): Long?

    @Query("SELECT MAX(fetched_at) FROM probe_distance")
    suspend fun newestDistanceFetchedAt(): Long?

    @Query("DELETE FROM dsn_spacecraft WHERE fetched_at < :cutoffMillis")
    suspend fun deleteSpacecraftFetchedBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM dsn_dish_info WHERE fetched_at < :cutoffMillis")
    suspend fun deleteDishInfoFetchedBefore(cutoffMillis: Long): Int

    @Query("DELETE FROM probe_distance WHERE fetched_at < :cutoffMillis")
    suspend fun deleteDistancesFetchedBefore(cutoffMillis: Long): Int

    /** Empties every table, for "Clear cache": all of it can be downloaded again. */
    @Transaction
    suspend fun clearAll() {
        clearLive()
        clearSpacecraft()
        clearDishInfo()
        clearProbeDistances()
    }

    @Transaction
    suspend fun clearLive() {
        clearSignals()
        clearLinks()
        clearDishes()
    }

    @Insert
    suspend fun insertDishes(dishes: List<DsnDishEntity>)

    @Insert
    suspend fun insertLinks(links: List<DsnLinkEntity>)

    @Insert
    suspend fun insertSignals(signals: List<DsnSignalEntity>)

    @Upsert
    suspend fun upsertSpacecraft(spacecraft: List<DsnSpacecraftEntity>)

    @Upsert
    suspend fun upsertDishInfo(dishes: List<DsnDishInfoEntity>)

    @Query("DELETE FROM dsn_dish")
    suspend fun clearDishes()

    @Query("DELETE FROM dsn_link")
    suspend fun clearLinks()

    @Query("DELETE FROM dsn_signal")
    suspend fun clearSignals()

    @Query("DELETE FROM dsn_spacecraft")
    suspend fun clearSpacecraft()

    @Query("DELETE FROM dsn_dish_info")
    suspend fun clearDishInfo()

    @Query("DELETE FROM probe_distance")
    suspend fun clearProbeDistances()
}
