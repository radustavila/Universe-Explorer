package com.radustavila.nasaexplorer.data.spaceweather.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceWeatherDao {

    @Query("SELECT * FROM kp_readings ORDER BY start")
    fun observeKpReadings(): Flow<List<KpReadingEntity>>

    /** Observed slots are revised (estimated → observed) and forecasts change: newest answer wins. */
    @Upsert
    suspend fun upsertKpReadings(readings: List<KpReadingEntity>)

    @Query("SELECT * FROM current_kp WHERE id = 0")
    fun observeCurrentKp(): Flow<CurrentKpEntity?>

    @Upsert
    suspend fun upsertCurrentKp(entity: CurrentKpEntity)

    @Query("SELECT * FROM aurora_forecast WHERE id = 0")
    fun observeAuroraForecast(): Flow<AuroraForecastEntity?>

    @Query("SELECT fetched_at FROM aurora_forecast WHERE id = 0")
    suspend fun auroraForecastFetchedAt(): Long?

    @Upsert
    suspend fun upsertAuroraForecast(entity: AuroraForecastEntity)

    @Query("SELECT * FROM solar_flares ORDER BY begin_time DESC LIMIT 1")
    fun observeLatestFlare(): Flow<SolarFlareEntity?>

    @Query("SELECT * FROM coronal_mass_ejections ORDER BY start_time DESC LIMIT 1")
    fun observeLatestCme(): Flow<CoronalMassEjectionEntity?>

    @Upsert
    suspend fun upsertFlares(flares: List<SolarFlareEntity>)

    @Upsert
    suspend fun upsertCmes(cmes: List<CoronalMassEjectionEntity>)

    @Transaction
    suspend fun upsertSolarActivity(flares: List<SolarFlareEntity>, cmes: List<CoronalMassEjectionEntity>) {
        upsertFlares(flares)
        upsertCmes(cmes)
    }

    @Query("DELETE FROM kp_readings WHERE start < :cutoff")
    suspend fun deleteKpReadingsBefore(cutoff: Long): Int

    /** Keeps the newest flare whatever its age, so the screen always has one to show. */
    @Query(
        "DELETE FROM solar_flares WHERE begin_time < :cutoff " +
            "AND id NOT IN (SELECT id FROM solar_flares ORDER BY begin_time DESC LIMIT 1)",
    )
    suspend fun deleteFlaresBefore(cutoff: Long): Int

    @Query(
        "DELETE FROM coronal_mass_ejections WHERE start_time < :cutoff " +
            "AND id NOT IN (SELECT id FROM coronal_mass_ejections ORDER BY start_time DESC LIMIT 1)",
    )
    suspend fun deleteCmesBefore(cutoff: Long): Int
}
