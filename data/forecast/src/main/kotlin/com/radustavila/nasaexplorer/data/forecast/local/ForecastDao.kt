package com.radustavila.nasaexplorer.data.forecast.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The module's one DAO: reads per place, whole-forecast writes, and the evictor's and cleaner's deletes. */
@Suppress("TooManyFunctions")
@Dao
interface ForecastDao {

    @Query("SELECT * FROM forecast_place WHERE latitude_e2 = :latitudeE2 AND longitude_e2 = :longitudeE2")
    fun observePlace(latitudeE2: Int, longitudeE2: Int): Flow<ForecastPlaceEntity?>

    @Query("SELECT * FROM forecast_step WHERE latitude_e2 = :latitudeE2 AND longitude_e2 = :longitudeE2 ORDER BY time")
    fun observeSteps(latitudeE2: Int, longitudeE2: Int): Flow<List<ForecastStepEntity>>

    @Query("SELECT * FROM forecast_place WHERE latitude_e2 = :latitudeE2 AND longitude_e2 = :longitudeE2")
    suspend fun place(latitudeE2: Int, longitudeE2: Int): ForecastPlaceEntity?

    /** Swaps a place's forecast for a new download, seen whole by observers. */
    @Transaction
    suspend fun replaceForecast(place: ForecastPlaceEntity, steps: List<ForecastStepEntity>) {
        deleteSteps(place.latitudeE2, place.longitudeE2)
        upsertPlace(place)
        insertSteps(steps)
    }

    /** A 304: the stored forecast stands until the new expiry. */
    @Query(
        "UPDATE forecast_place SET fetched_at = :fetchedAt, expires_at = :expiresAt " +
            "WHERE latitude_e2 = :latitudeE2 AND longitude_e2 = :longitudeE2",
    )
    suspend fun markRevalidated(latitudeE2: Int, longitudeE2: Int, fetchedAt: Long, expiresAt: Long)

    @Upsert
    suspend fun upsertPlace(place: ForecastPlaceEntity)

    @Insert
    suspend fun insertSteps(steps: List<ForecastStepEntity>)

    @Query("DELETE FROM forecast_step WHERE latitude_e2 = :latitudeE2 AND longitude_e2 = :longitudeE2")
    suspend fun deleteSteps(latitudeE2: Int, longitudeE2: Int)

    @Query("SELECT MAX(fetched_at) FROM forecast_place")
    suspend fun newestFetchedAt(): Long?

    /** Places not fetched since [cutoff], with their steps. */
    @Transaction
    suspend fun deletePlacesFetchedBefore(cutoff: Long): Int {
        val steps = deleteStepsOfPlacesFetchedBefore(cutoff)
        return steps + deletePlacesOnlyFetchedBefore(cutoff)
    }

    @Query(
        "DELETE FROM forecast_step WHERE EXISTS (SELECT 1 FROM forecast_place p WHERE " +
            "p.latitude_e2 = forecast_step.latitude_e2 AND p.longitude_e2 = forecast_step.longitude_e2 " +
            "AND p.fetched_at < :cutoff)",
    )
    suspend fun deleteStepsOfPlacesFetchedBefore(cutoff: Long): Int

    @Query("DELETE FROM forecast_place WHERE fetched_at < :cutoff")
    suspend fun deletePlacesOnlyFetchedBefore(cutoff: Long): Int

    /** Steps already in the past say nothing about a night to come. */
    @Query("DELETE FROM forecast_step WHERE time < :cutoff")
    suspend fun deleteStepsBefore(cutoff: Long): Int

    @Transaction
    suspend fun clearAll() {
        deleteAllSteps()
        deleteAllPlaces()
    }

    @Query("DELETE FROM forecast_step")
    suspend fun deleteAllSteps()

    @Query("DELETE FROM forecast_place")
    suspend fun deleteAllPlaces()
}
