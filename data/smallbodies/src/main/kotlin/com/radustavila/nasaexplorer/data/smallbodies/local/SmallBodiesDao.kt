package com.radustavila.nasaexplorer.data.smallbodies.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SmallBodiesDao {

    @Transaction
    @Query("SELECT * FROM close_approaches ORDER BY time")
    fun observeCloseApproaches(): Flow<List<CloseApproachWithSentry>>

    @Transaction
    @Query("SELECT * FROM close_approaches WHERE designation = :designation ORDER BY time LIMIT 1")
    fun observeCloseApproach(designation: String): Flow<CloseApproachWithSentry?>

    @Query("SELECT * FROM fireballs ORDER BY time DESC")
    fun observeFireballs(): Flow<List<FireballEntity>>

    @Query("SELECT * FROM small_body_details WHERE designation = :designation")
    fun observeDetails(designation: String): Flow<SmallBodyDetailsEntity?>

    @Upsert
    suspend fun upsertDetails(details: SmallBodyDetailsEntity)

    @Query("DELETE FROM close_approaches")
    suspend fun deleteCloseApproaches()

    @Query("DELETE FROM sentry_objects")
    suspend fun deleteSentryObjects()

    @Query("DELETE FROM fireballs")
    suspend fun deleteFireballs()

    @Insert
    suspend fun insertCloseApproaches(approaches: List<CloseApproachEntity>)

    @Insert
    suspend fun insertSentryObjects(objects: List<SentryObjectEntity>)

    @Insert
    suspend fun insertFireballs(fireballs: List<FireballEntity>)

    /** Each list is JPL's whole current answer, so it replaces the previous one in one transaction. */
    @Transaction
    suspend fun replaceAll(
        approaches: List<CloseApproachEntity>,
        sentry: List<SentryObjectEntity>,
        fireballs: List<FireballEntity>,
    ) {
        deleteCloseApproaches()
        insertCloseApproaches(approaches)
        deleteSentryObjects()
        insertSentryObjects(sentry)
        deleteFireballs()
        insertFireballs(fireballs)
    }

    @Query("DELETE FROM close_approaches WHERE time < :cutoff")
    suspend fun deleteCloseApproachesBefore(cutoff: Long): Int

    /** Details of objects no longer passing close are only worth keeping for a while. */
    @Query(
        "DELETE FROM small_body_details WHERE fetched_at < :cutoff " +
            "AND designation NOT IN (SELECT designation FROM close_approaches)",
    )
    suspend fun deleteDetailsFetchedBefore(cutoff: Long): Int
}
