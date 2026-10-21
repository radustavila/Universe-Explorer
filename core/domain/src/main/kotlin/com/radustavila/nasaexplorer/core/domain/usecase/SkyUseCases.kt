package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.getOrNull
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidget
import com.radustavila.nasaexplorer.core.domain.announcement.HomeWidgetUpdater
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.sky.IssPassForecast
import com.radustavila.nasaexplorer.core.domain.sky.IssTrack
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCalculator
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.domain.sync.succeeded
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.OrbitalElements.Companion.ISS_NORAD_ID
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SkyTonight
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest

/**
 * Tonight's Moon, twilight and planets above [observer], recomputed every [RECOMPUTE_INTERVAL] so
 * the screen rolls over to the next night on its own. Entirely offline.
 */
class ObserveSkyTonightUseCase @Inject constructor(
    private val calculator: SkyCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(observer: GeoCoordinate): Flow<SkyTonight> = flow {
        while (true) {
            emit(calculator.skyTonight(observer, timeProvider.now()))
            delay(RECOMPUTE_INTERVAL.toMillis())
        }
    }.flowOn(computeDispatcher)

    companion object {
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(10)
    }
}

/**
 * ISS passes over [observer] for the next [FORECAST_WINDOW], predicted on the device from the
 * cached orbital elements and recomputed as passes go by or new elements arrive.
 */
class ObserveIssPassesUseCase @Inject constructor(
    private val satelliteRepository: SatelliteRepository,
    private val predictor: SatellitePassPredictor,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(observer: GeoCoordinate): Flow<IssPassForecast> =
        satelliteRepository.observeElements(ISS_NORAD_ID).transformLatest { elements ->
            while (true) {
                emit(forecast(elements, observer, timeProvider.now()))
                delay(RECOMPUTE_INTERVAL.toMillis())
            }
        }.flowOn(computeDispatcher)

    private fun forecast(elements: OrbitalElements?, observer: GeoCoordinate, now: Instant): IssPassForecast {
        if (elements == null) return IssPassForecast(elements = null, passes = emptyList(), isStale = false)
        if (elements.isTooOldAt(now)) return IssPassForecast(elements, passes = emptyList(), isStale = true)
        val passes = predictor.passes(elements, observer, from = now, to = now.plus(FORECAST_WINDOW))
        return IssPassForecast(elements, passes, isStale = false)
    }

    companion object {
        val FORECAST_WINDOW: Duration = Duration.ofDays(5)
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}

/**
 * Where the ISS is, polled from the live tracker every [POLL_INTERVAL] while collected, with the
 * ground track around it from the cached elements. Offline, or when the tracker fails, the position
 * is propagated from the elements instead (and marked as not live).
 */
class ObserveIssTrackUseCase @Inject constructor(
    private val satelliteRepository: SatelliteRepository,
    private val predictor: SatellitePassPredictor,
    private val networkMonitor: NetworkMonitor,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<IssTrack> =
        satelliteRepository.observeElements(ISS_NORAD_ID).transformLatest { elements ->
            var trackAnchor: Instant? = null
            var groundTrack = emptyList<SatellitePosition>()
            while (true) {
                val now = timeProvider.now()
                val usable = elements?.takeUnless { it.isTooOldAt(now) }
                val live = if (networkMonitor.isOnline.first()) {
                    satelliteRepository.fetchLivePosition(ISS_NORAD_ID).getOrNull()
                } else {
                    null
                }
                // The track window only moves every TRACK_REFRESH, so between those steps the same
                // list is emitted again and the map redraws just the station's dot.
                val anchor = now.truncatedToMultipleOf(TRACK_REFRESH)
                if (usable == null) {
                    groundTrack = emptyList()
                    trackAnchor = null
                } else if (anchor != trackAnchor) {
                    groundTrack = predictor.groundTrack(usable, from = anchor.minus(TRACK_BEHIND), to = anchor.plus(TRACK_AHEAD))
                    trackAnchor = anchor
                }
                emit(
                    IssTrack(
                        position = live ?: usable?.let { predictor.position(it, now) },
                        groundTrack = groundTrack,
                    ),
                )
                delay(POLL_INTERVAL.toMillis())
            }
        }.flowOn(computeDispatcher)

    companion object {
        /** wheretheiss.at allows about one call a second; this keeps the dot moving visibly. */
        val POLL_INTERVAL: Duration = Duration.ofSeconds(5)
        val TRACK_BEHIND: Duration = Duration.ofMinutes(45)
        val TRACK_AHEAD: Duration = Duration.ofMinutes(90)

        /** How often the ground track's window slides along; far less often than the dot moves. */
        val TRACK_REFRESH: Duration = Duration.ofMinutes(5)
    }
}

/**
 * Refreshes the cached ISS elements at most once per [MIN_REFRESH_INTERVAL], as CelesTrak asks.
 * New elements move the next pass, so a successful refresh re-renders the sky tonight widget.
 *
 * There is no connectivity pre-check: the monitor's first value can briefly read offline while a
 * second network reports in, which silently skipped the refresh. Offline, the call fails fast at
 * the HTTP layer and the failure is recorded, leaving the source due.
 */
class RefreshOrbitalElementsUseCase @Inject constructor(
    private val satelliteRepository: SatelliteRepository,
    private val refreshSource: RefreshSourceUseCase,
    private val widgetUpdater: HomeWidgetUpdater,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Unit> {
        return refreshSource(SyncSource.CELESTRAK, minInterval = MIN_REFRESH_INTERVAL, force = force) {
            satelliteRepository.refreshElements()
        }.also { outcome -> if (outcome.succeeded()) widgetUpdater.update(HomeWidget.SKY_TONIGHT) }
    }

    companion object {
        /** CelesTrak updates GP data a few times a day and asks clients not to poll more often. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(12)
    }
}

class ObservePeopleInSpaceUseCase @Inject constructor(
    private val repository: PeopleInSpaceRepository,
) {
    operator fun invoke(): Flow<List<PersonInSpace>> = repository.observePeopleInSpace()
}

/**
 * Refreshes who is in space at most once per [MIN_REFRESH_INTERVAL]. Launch Library 2 allows 15
 * calls an hour shared with the launches card, and crews change only a few times a year, so a
 * [force]d refresh (pull-to-refresh) still waits [FORCED_MIN_INTERVAL] after the last success. An
 * empty cache is always due.
 */
class RefreshPeopleInSpaceUseCase @Inject constructor(
    private val repository: PeopleInSpaceRepository,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val timeProvider: TimeProvider,
    private val refreshSource: RefreshSourceUseCase,
) {
    /** Like [RefreshOrbitalElementsUseCase], without a connectivity pre-check. */
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> {
        val cacheIsEmpty = repository.observePeopleInSpace().first().isEmpty()
        val lastSuccess = syncMetadataRepository.observeSourceStatus(SyncSource.PEOPLE_IN_SPACE).first().lastSuccessAt
        val forcedAndDue = force &&
            (lastSuccess == null || Duration.between(lastSuccess, timeProvider.now()) >= FORCED_MIN_INTERVAL)
        return refreshSource(
            SyncSource.PEOPLE_IN_SPACE,
            minInterval = MIN_REFRESH_INTERVAL,
            force = cacheIsEmpty || forcedAndDue,
        ) { repository.refreshPeopleInSpace() }
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(12)
        val FORCED_MIN_INTERVAL: Duration = Duration.ofHours(1)
    }
}

/**
 * Elements older than this are not used for predictions: SGP4's error for a low orbit grows to
 * several minutes within a couple of weeks, which would announce passes that do not happen.
 */
val ORBITAL_ELEMENTS_MAX_AGE: Duration = Duration.ofDays(14)

internal fun OrbitalElements.isTooOldAt(now: Instant): Boolean =
    Duration.between(epoch, now) > ORBITAL_ELEMENTS_MAX_AGE

private fun Instant.truncatedToMultipleOf(step: Duration): Instant =
    Instant.ofEpochMilli(toEpochMilli() - Math.floorMod(toEpochMilli(), step.toMillis()))
