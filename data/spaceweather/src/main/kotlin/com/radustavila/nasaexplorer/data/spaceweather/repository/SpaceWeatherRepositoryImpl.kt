package com.radustavila.nasaexplorer.data.spaceweather.repository

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherLocalDataSource
import com.radustavila.nasaexplorer.data.spaceweather.mapper.latestToEntityOrNull
import com.radustavila.nasaexplorer.data.spaceweather.mapper.toDomain
import com.radustavila.nasaexplorer.data.spaceweather.mapper.toEntityOrNull
import com.radustavila.nasaexplorer.data.spaceweather.remote.SpaceWeatherRemoteDataSource
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Offline-first space weather: the screen observes Room, and the refresh calls are the only path
 * from NOAA and DONKI into it. Every failure leaves the cache as it was.
 */
@Singleton
class SpaceWeatherRepositoryImpl @Inject constructor(
    private val local: SpaceWeatherLocalDataSource,
    private val remote: SpaceWeatherRemoteDataSource,
    private val timeProvider: TimeProvider,
) : SpaceWeatherRepository {

    override fun observeKpForecast(): Flow<List<KpReading>> =
        local.observeKpReadings().map { entities -> entities.map { it.toDomain() } }

    override fun observeCurrentKp(): Flow<CurrentKp?> = local.observeCurrentKp().map { it?.toDomain() }

    override fun observeAuroraForecast(): Flow<AuroraForecast?> =
        local.observeAuroraForecast()
            // The grid is 65 KB; skip re-decoding it when another column of the row changed.
            .distinctUntilChanged { old, new -> old?.observedAt == new?.observedAt && old?.fetchedAt == new?.fetchedAt }
            .map { it?.toDomain() }

    override fun observeLatestFlare(): Flow<SolarFlare?> = local.observeLatestFlare().map { it?.toDomain() }

    override fun observeLatestCme(): Flow<CoronalMassEjection?> = local.observeLatestCme().map { it?.toDomain() }

    override suspend fun auroraForecastFetchedAt(): Instant? =
        local.auroraForecastFetchedAt().getOrNull()?.let(Instant::ofEpochMilli)

    /**
     * The 3-hour forecast decides success; the 1-minute estimate only feeds the gauge, so the cached
     * one is kept when it fails.
     */
    override suspend fun refreshKp(): DataResult<Unit> {
        val forecast = when (val result = remote.fetchKpForecast()) {
            is DataResult.Success -> result.data.mapNotNull { it.toEntityOrNull() }
            is DataResult.Failure -> return result
        }
        if (forecast.isEmpty()) return DataResult.Failure(AppError.EmptyResponse)
        val current = remote.fetchKpOneMinute().getOrNull()?.latestToEntityOrNull()
        return local.saveKp(forecast, current)
    }

    override suspend fun refreshAuroraForecast(): DataResult<Unit> =
        when (val result = remote.fetchAuroraForecast()) {
            is DataResult.Success -> result.data.toEntityOrNull(fetchedAt = timeProvider.now())
                ?.let { local.saveAuroraForecast(it) }
                ?: DataResult.Failure(AppError.EmptyResponse)

            is DataResult.Failure -> result
        }

    /** Flares from the last [FLARE_WINDOW_DAYS] days, CMEs from the last [CME_WINDOW_DAYS]. */
    override suspend fun refreshSolarActivity(): DataResult<Unit> {
        val today = timeProvider.today()
        val flares = when (val result = remote.fetchFlares(today.minusDays(FLARE_WINDOW_DAYS), today)) {
            is DataResult.Success -> result.data.mapNotNull { it.toEntityOrNull() }
            is DataResult.Failure -> return result
        }
        val cmes = when (val result = remote.fetchCmes(today.minusDays(CME_WINDOW_DAYS), today)) {
            is DataResult.Success -> result.data.mapNotNull { it.toEntityOrNull() }
            is DataResult.Failure -> return result
        }
        // A quiet Sun answers two empty lists, which is news too: nothing to store, nothing wrong.
        return local.saveSolarActivity(flares, cmes)
    }

    private companion object {
        /** Big flares are rarer than CMEs; a month keeps one on screen through quiet spells. */
        const val FLARE_WINDOW_DAYS = 30L

        /** CME lists are long (~100 a month, 185 KB); two weeks is plenty for "latest". */
        const val CME_WINDOW_DAYS = 14L
    }
}
