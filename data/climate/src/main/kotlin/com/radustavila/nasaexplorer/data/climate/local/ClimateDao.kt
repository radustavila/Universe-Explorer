package com.radustavila.nasaexplorer.data.climate.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ClimateDao {

    @Query("SELECT * FROM climate_point ORDER BY indicator, date")
    fun observePoints(): Flow<List<ClimatePointEntity>>

    /** Stores a refresh: [points] become all of [indicator]'s values. */
    @Transaction
    suspend fun replaceIndicator(indicator: String, points: List<ClimatePointEntity>) {
        deleteIndicator(indicator)
        insertPoints(points)
    }

    @Insert
    suspend fun insertPoints(points: List<ClimatePointEntity>)

    @Query("DELETE FROM climate_point WHERE indicator = :indicator")
    suspend fun deleteIndicator(indicator: String)

    /** Empties the table, for "Clear cache": the files can be downloaded again. */
    @Query("DELETE FROM climate_point")
    suspend fun clearAll()
}
