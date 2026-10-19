package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LaunchDao {

    @Query("SELECT * FROM launches ORDER BY net_epoch_millis ASC")
    fun observeUpcoming(): Flow<List<LaunchEntity>>

    /** Swaps the whole cache in one transaction, so observers never see it half-written or empty. */
    @Transaction
    suspend fun replaceAll(launches: List<LaunchEntity>) {
        deleteAll()
        insertAll(launches)
    }

    @Query("DELETE FROM launches")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(launches: List<LaunchEntity>)
}
