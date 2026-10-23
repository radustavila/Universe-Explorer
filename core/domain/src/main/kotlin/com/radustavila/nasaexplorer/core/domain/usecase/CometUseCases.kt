package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.core.model.CometsTonight
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * Refreshes the comets at most once per [MIN_REFRESH_INTERVAL], unless [force]d by the user: one
 * SBDB query and a Horizons request per candidate, which JPL is asked for once a day. Runs from the
 * daily comets job and when the comets screen opens (skipped while fresh).
 */
class RefreshCometsUseCase @Inject constructor(
    private val repository: CometRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> =
        refreshSource(SyncSource.COMETS, minInterval = MIN_REFRESH_INTERVAL, force = force) {
            repository.refresh()
        }

    companion object {
        /** A little under a day, so the daily job is not skipped by a refresh made the day before. */
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofHours(20)
    }
}

/**
 * Every cached comet for the saved place (or without one), split into those bright enough to look
 * for and the fainter rest, brightest first. Recomputed whenever the cache or the place changes;
 * comets whose ephemeris does not reach today are left out.
 */
class ObserveCometsTonightUseCase @Inject constructor(
    private val repository: CometRepository,
    private val locationRepository: ObserverLocationRepository,
    private val calculator: CometSkyCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<CometsTonight> =
        combine(repository.observeComets(), locationRepository.location) { comets, location ->
            val observer = location?.coordinate
            val now = timeProvider.now()
            val tonight = comets
                .mapNotNull { calculator.tonight(it, observer, now) }
                .sortedWith(BRIGHTEST_FIRST)
            val (visible, faint) = tonight.partition { it.visibility != CometVisibility.TOO_FAINT }
            CometsTonight(observer = observer, visible = visible, faint = faint)
        }.flowOn(computeDispatcher)

    private companion object {
        /** Comets without a brightness model last. */
        val BRIGHTEST_FIRST: Comparator<CometTonight> = compareBy(nullsLast()) { it.magnitude }
    }
}

/** One cached comet for the saved place; null when it is not cached or its ephemeris is out of date. */
class ObserveCometTonightUseCase @Inject constructor(
    private val repository: CometRepository,
    private val locationRepository: ObserverLocationRepository,
    private val calculator: CometSkyCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    operator fun invoke(designation: String): Flow<CometTonight?> =
        combine(repository.observeComet(designation), locationRepository.location) { comet, location ->
            comet?.let { calculator.tonight(it, location?.coordinate, timeProvider.now()) }
        }.flowOn(computeDispatcher)
}
