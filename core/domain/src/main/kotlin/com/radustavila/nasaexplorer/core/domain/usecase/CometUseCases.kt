package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.repository.CometDebugSettingsRepository
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.CometTonight
import com.radustavila.nasaexplorer.core.model.CometVisibility
import com.radustavila.nasaexplorer.core.model.CometsTonight
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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

/**
 * The faintest predicted magnitude at which the sky map and the sky events calendar show a comet:
 * [CometVisibility.TELESCOPE_LIMIT], the same cut-off as "visible now" in the comets list, so they
 * are not filled with comets nobody can see. Null (no cut-off) while the debug switch shows every
 * comet.
 */
class ObserveCometMagnitudeLimitUseCase @Inject constructor(
    private val debugSettings: CometDebugSettingsRepository,
) {
    operator fun invoke(): Flow<Double?> = debugSettings.observeShowFaintComets()
        .map { showFaint -> if (showFaint) null else CometVisibility.TELESCOPE_LIMIT }
        .distinctUntilChanged()
}

/** Debug builds: whether the sky map and the calendar show comets fainter than the cut-off. */
class ObserveShowFaintCometsUseCase @Inject constructor(private val debugSettings: CometDebugSettingsRepository) {
    operator fun invoke(): Flow<Boolean> = debugSettings.observeShowFaintComets()
}

class SetShowFaintCometsUseCase @Inject constructor(private val debugSettings: CometDebugSettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = debugSettings.setShowFaintComets(enabled)
}

/**
 * The perihelion of every cached comet whose brightest predicted magnitude in its ephemeris passes
 * the cut-off, as sky events in time order. Comets without a known perihelion, or whose ephemeris
 * has not been downloaded yet, are left out. Follows the cache: a comet that drops out of JPL's
 * candidates takes its event with it.
 */
class ObserveCometPerihelionEventsUseCase @Inject constructor(
    private val repository: CometRepository,
    private val observeMagnitudeLimit: ObserveCometMagnitudeLimitUseCase,
) {
    operator fun invoke(): Flow<List<CometPerihelion>> =
        combine(repository.observeComets(), observeMagnitudeLimit()) { comets, limit ->
            comets.filter { isBrightEnough(it.peakMagnitude(), limit) }
            .mapNotNull { it.perihelionEvent() }
            .sortedBy { it.time }
        }.distinctUntilChanged()

    private fun Comet.peakMagnitude(): Double? = ephemeris.mapNotNull { it.magnitude }.minOrNull()

    private fun Comet.perihelionEvent(): CometPerihelion? {
        val time = perihelionTime ?: return null
        // The daily point of the perihelion, when the ephemeris reaches that far.
        val atPerihelion = ephemeris.minByOrNull { Duration.between(time, it.time).abs() }
            ?.takeIf { Duration.between(time, it.time).abs() <= HALF_DAY }
        return CometPerihelion(
            time = time,
            designation = designation,
            name = name,
            perihelionDistanceAu = perihelionDistanceAu,
            magnitude = atPerihelion?.magnitude,
        )
    }

    private companion object {
        val HALF_DAY: Duration = Duration.ofHours(12)
    }
}

/**
 * Whether a comet predicted at [magnitude] passes the cut-off [limit]; a null [limit] lets every
 * comet through, even one without a brightness model.
 */
internal fun isBrightEnough(magnitude: Double?, limit: Double?): Boolean =
    limit == null || (magnitude != null && magnitude <= limit)
