package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SpaceWeatherRepository]: the cache is whatever the test seeds. */
class FakeSpaceWeatherRepository : SpaceWeatherRepository {

    val kpForecast = MutableStateFlow<List<KpReading>>(emptyList())
    val currentKp = MutableStateFlow<CurrentKp?>(null)
    val auroraForecast = MutableStateFlow<AuroraForecast?>(null)
    val latestFlare = MutableStateFlow<SolarFlare?>(null)
    val latestCme = MutableStateFlow<CoronalMassEjection?>(null)

    var auroraFetchedAt: Instant? = null

    var kpRefreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var auroraRefreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var solarActivityRefreshResult: DataResult<Unit> = DataResult.Success(Unit)

    var kpRefreshCount = 0
        private set
    var auroraRefreshCount = 0
        private set
    var solarActivityRefreshCount = 0
        private set

    override fun observeKpForecast(): Flow<List<KpReading>> = kpForecast

    override fun observeCurrentKp(): Flow<CurrentKp?> = currentKp

    override fun observeAuroraForecast(): Flow<AuroraForecast?> = auroraForecast

    override fun observeLatestFlare(): Flow<SolarFlare?> = latestFlare

    override fun observeLatestCme(): Flow<CoronalMassEjection?> = latestCme

    override suspend fun auroraForecastFetchedAt(): Instant? = auroraFetchedAt

    override suspend fun refreshKp(): DataResult<Unit> {
        kpRefreshCount++
        return kpRefreshResult
    }

    override suspend fun refreshAuroraForecast(): DataResult<Unit> {
        auroraRefreshCount++
        return auroraRefreshResult
    }

    override suspend fun refreshSolarActivity(): DataResult<Unit> {
        solarActivityRefreshCount++
        return solarActivityRefreshResult
    }
}
