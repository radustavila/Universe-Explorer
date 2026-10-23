package com.radustavila.nasaexplorer.data.launches.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LaunchDao {

    @Query("SELECT * FROM launches WHERE timeline = 'UPCOMING' ORDER BY net_epoch_millis ASC")
    fun observeUpcoming(): Flow<List<LaunchEntity>>

    @Query("SELECT * FROM launches WHERE timeline = 'PAST' ORDER BY net_epoch_millis DESC")
    fun observePast(): Flow<List<LaunchEntity>>

    @Query("SELECT * FROM launches WHERE id = :id")
    fun observeById(id: String): Flow<LaunchEntity?>

    @Query("SELECT timeline FROM launches WHERE id = :id")
    suspend fun timelineOf(id: String): String?

    /**
     * Swaps one list ([timeline]) in one transaction, so observers never see it half-written or
     * empty. A launch that moved lists (it flew) is taken over by the list that now has it.
     */
    @Transaction
    suspend fun replaceTimeline(timeline: String, launches: List<LaunchEntity>) {
        deleteTimeline(timeline)
        insertAll(launches)
    }

    @Query("DELETE FROM launches WHERE timeline = :timeline")
    suspend fun deleteTimeline(timeline: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(launches: List<LaunchEntity>)
}
