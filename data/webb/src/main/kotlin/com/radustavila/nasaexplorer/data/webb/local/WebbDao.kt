package com.radustavila.nasaexplorer.data.webb.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WebbDao {

    @Query("SELECT * FROM webb_visit ORDER BY start")
    fun observeVisits(): Flow<List<WebbVisitEntity>>

    /** Stores a refresh: [visits] become the whole schedule. */
    @Transaction
    suspend fun replaceSchedule(visits: List<WebbVisitEntity>) {
        clearAll()
        insertVisits(visits)
    }

    @Insert
    suspend fun insertVisits(visits: List<WebbVisitEntity>)

    /** Empties the schedule, for "Clear cache": it can be downloaded again. */
    @Query("DELETE FROM webb_visit")
    suspend fun clearAll()
}
