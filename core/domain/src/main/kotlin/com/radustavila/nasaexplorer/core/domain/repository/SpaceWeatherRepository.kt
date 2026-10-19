package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Space weather: NOAA SWPC's Kp index and aurora nowcast, DONKI's flares and CMEs. Reads come from
 * the local cache; the refresh calls are the only path from the network into it, and a failed
 * refresh keeps what was cached.
 */
interface SpaceWeatherRepository {

    /** Every cached 3-hour Kp slot, observed and forecast, oldest first. */
    fun observeKpForecast(): Flow<List<KpReading>>

    fun observeCurrentKp(): Flow<CurrentKp?>

    fun observeAuroraForecast(): Flow<AuroraForecast?>

    fun observeLatestFlare(): Flow<SolarFlare?>

    fun observeLatestCme(): Flow<CoronalMassEjection?>

    /** When the cached aurora grid was downloaded, or null when there is none. */
    suspend fun auroraForecastFetchedAt(): Instant?

    /** The 3-hour Kp forecast and the 1-minute estimate: two small files. */
    suspend fun refreshKp(): DataResult<Unit>

    /** The OVATION aurora grid: almost a megabyte, so only fetched while someone looks at it. */
    suspend fun refreshAuroraForecast(): DataResult<Unit>

    /** Recent flares and CMEs from DONKI. */
    suspend fun refreshSolarActivity(): DataResult<Unit>
}
