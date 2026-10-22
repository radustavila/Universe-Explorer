package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.succeeded
import com.radustavila.nasaexplorer.core.model.AuroraForecast
import com.radustavila.nasaexplorer.core.model.CoronalMassEjection
import com.radustavila.nasaexplorer.core.model.CurrentKp
import com.radustavila.nasaexplorer.core.model.KpReading
import com.radustavila.nasaexplorer.core.model.SolarFlare
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveKpForecastUseCase @Inject constructor(
    private val repository: SpaceWeatherRepository,
) {
    operator fun invoke(): Flow<List<KpReading>> = repository.observeKpForecast()
}

class ObserveCurrentKpUseCase @Inject constructor(
    private val repository: SpaceWeatherRepository,
) {
    operator fun invoke(): Flow<CurrentKp?> = repository.observeCurrentKp()
}

class ObserveAuroraForecastUseCase @Inject constructor(
    private val repository: SpaceWeatherRepository,
) {
    operator fun invoke(): Flow<AuroraForecast?> = repository.observeAuroraForecast()
}

/** The latest flare and CME, together, for the screen's "Sun" section. */
class ObserveSolarActivityUseCase @Inject constructor(
    private val repository: SpaceWeatherRepository,
) {
    operator fun invoke(): Flow<Pair<SolarFlare?, CoronalMassEjection?>> =
        combine(repository.observeLatestFlare(), repository.observeLatestCme(), ::Pair)
}

/**
 * One space weather refresh, run by the background job.
 *
 * - NOAA's Kp files go through [RefreshSourceUseCase] as [SyncSource.SPACE_WEATHER], at most once
 *   per [KP_INTERVAL]; the aurora grid rides along when [includeAuroraMap] is set and is refreshed
 *   whenever the cached one is older than [AURORA_MAP_MAX_AGE], even if Kp is still fresh.
 * - DONKI ([SyncSource.DONKI]) changes far less often: at most once per [SOLAR_ACTIVITY_INTERVAL].
 * - Aurora alerts are checked against the cached forecast after every run, refreshed or not.
 * - NOAA and DONKI, and Kp and the aurora grid, are fetched concurrently.
 * - A successful NOAA refresh re-renders the aurora widget, the only way it gets fresh data: it has
 *   no job of its own.
 *
 * Returns the NOAA outcome, which decides whether the job retries.
 */
class SynchronizeSpaceWeatherUseCase @Inject constructor(
    private val repository: SpaceWeatherRepository,
    private val refreshSource: RefreshSourceUseCase,
    private val checkAuroraAlert: CheckAuroraAlertUseCase,
    private val widgetUpdater: HomeWidgetUpdater,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(force: Boolean = false, includeAuroraMap: Boolean = false): SourceRefreshResult<Unit> =
        coroutineScope {
            // NOAA and DONKI are different hosts with their own bookkeeping: refreshed side by side.
            val donki = async {
                refreshSource(SyncSource.DONKI, minInterval = SOLAR_ACTIVITY_INTERVAL, force = force) {
                    repository.refreshSolarActivity()
                }
            }
            val noaa = refreshNoaa(force, includeAuroraMap)
            // Needs the fresh Kp only, not the solar activity.
            checkAuroraAlert()
            if (noaa.succeeded()) widgetUpdater.update(HomeWidget.AURORA)
            donki.await()
            noaa
        }

    private suspend fun refreshNoaa(force: Boolean, includeAuroraMap: Boolean): SourceRefreshResult<Unit> {
        val auroraMapStale = includeAuroraMap && repository.auroraForecastFetchedAt().isOlderThan(AURORA_MAP_MAX_AGE)
        return refreshSource(SyncSource.SPACE_WEATHER, minInterval = KP_INTERVAL, force = force || auroraMapStale) {
            coroutineScope {
                val map = async { if (includeAuroraMap) repository.refreshAuroraForecast() else DataResult.Success(Unit) }
                val kp = repository.refreshKp()
                // A missing map must not hide a good Kp refresh from the bookkeeping, and vice versa.
                if (kp is DataResult.Failure) kp else map.await()
            }
        }
    }

    private fun Instant?.isOlderThan(age: Duration): Boolean =
        this == null || timeProvider.now().isAfter(plus(age))

    companion object {
        /**
         * Half the background job's 30 minutes: WorkManager runs periodic work a little early or
         * late, and a run must not be skipped because the previous one finished a minute late.
         */
        val KP_INTERVAL: Duration = Duration.ofMinutes(15)
        val AURORA_MAP_MAX_AGE: Duration = Duration.ofMinutes(30)
        val SOLAR_ACTIVITY_INTERVAL: Duration = Duration.ofHours(3)
    }
}

/** What the space weather screen shows about its last refresh. */
data class SpaceWeatherSyncStatus(
    val isSyncing: Boolean = false,
    val lastSuccessAt: Instant? = null,
    /** Whether the most recent attempt failed; the cached data is still shown. */
    val lastAttemptFailed: Boolean = false,
)

class ObserveSpaceWeatherSyncStatusUseCase @Inject constructor(
    private val scheduler: SpaceWeatherSyncScheduler,
    private val syncMetadataRepository: SyncMetadataRepository,
) {
    operator fun invoke(): Flow<SpaceWeatherSyncStatus> = combine(
        scheduler.isSyncing,
        syncMetadataRepository.observeSourceStatus(SyncSource.SPACE_WEATHER),
    ) { isSyncing, status ->
        SpaceWeatherSyncStatus(
            isSyncing = isSyncing,
            lastSuccessAt = status.lastSuccessAt,
            lastAttemptFailed = status.isFailing,
        )
    }
}

/**
 * Asks for a one-off refresh, with the aurora map unless [includeAuroraMap] is off (the Today card
 * shows only Kp), e.g. when the screen opens. It registers no background refresh: only aurora
 * alerts need one (see [ScheduleAuroraAlertRefreshUseCase]).
 */
class RequestSpaceWeatherSyncUseCase @Inject constructor(
    private val scheduler: SpaceWeatherSyncScheduler,
) {
    operator fun invoke(force: Boolean = false, includeAuroraMap: Boolean = true) {
        scheduler.requestSync(force = force, includeAuroraMap = includeAuroraMap)
    }
}
