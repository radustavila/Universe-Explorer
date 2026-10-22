package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteForecast
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteSetStatus
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.StarlinkTrains
import com.radustavila.nasaexplorer.core.domain.sky.TrackedSatellite
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

/** The bookkeeping entry of each set; the ISS keeps the one it always had. */
val SatelliteSet.syncSource: SyncSource
    get() = when (this) {
        SatelliteSet.ISS -> SyncSource.CELESTRAK
        SatelliteSet.HUBBLE -> SyncSource.CELESTRAK_HUBBLE
        SatelliteSet.TIANGONG -> SyncSource.CELESTRAK_TIANGONG
        SatelliteSet.STARLINK_TRAINS -> SyncSource.CELESTRAK_STARLINK
        SatelliteSet.BRIGHT_SATELLITES -> SyncSource.CELESTRAK_VISUAL
    }

/** The sets the "More satellites" picker offers: everything but the ISS, which has its own card. */
val PICKABLE_SATELLITE_SETS: List<SatelliteSet> = SatelliteSet.entries - SatelliteSet.ISS

/**
 * Sets a reminder can be turned on for. The bright satellites are left out: dozens of them pass on
 * a clear night, and a reminder for each would be noise.
 */
val REMINDABLE_SATELLITE_SETS: Set<SatelliteSet> =
    setOf(SatelliteSet.HUBBLE, SatelliteSet.TIANGONG, SatelliteSet.STARLINK_TRAINS)

class ObserveEnabledSatelliteSetsUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
) {
    operator fun invoke(): Flow<Set<SatelliteSet>> = preferences.enabledSatelliteSets
}

/** Turns a set on or off in the picker; turning one on fetches it when due. */
class SetSatelliteSetEnabledUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val refreshSatelliteSets: RefreshSatelliteSetsUseCase,
    private val rescheduleReminder: RescheduleSatellitePassReminderUseCase,
) {
    suspend operator fun invoke(set: SatelliteSet, enabled: Boolean) {
        if (set == SatelliteSet.ISS) return
        preferences.setSatelliteSetEnabled(set, enabled)
        if (enabled) refreshSatelliteSets(setOf(set))
        rescheduleReminder()
    }
}

/**
 * Refreshes the enabled sets (or [sets]), each at most once per [MIN_REFRESH_INTERVAL] and one
 * request each, as CelesTrak asks: it updates GP data about every two hours and blocks clients that
 * download more often. There is no forced refresh, so pull-to-refresh cannot break that rule; an
 * unfetched set is always due.
 *
 * The ISS is refreshed by [RefreshOrbitalElementsUseCase], on its own schedule.
 */
class RefreshSatelliteSetsUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val satelliteRepository: SatelliteRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(sets: Set<SatelliteSet>? = null): List<SourceRefreshResult<Int>> {
        val targets = (sets ?: preferences.enabledSatelliteSets.first()) - SatelliteSet.ISS
        return targets.sorted().map { set ->
            refreshSource(set.syncSource, minInterval = MIN_REFRESH_INTERVAL) { satelliteRepository.refreshSet(set) }
        }
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofDays(1)
    }
}

/**
 * Visible passes of the enabled sets over [observer], merged and soonest first, predicted on the
 * device from the cached elements.
 *
 * The search is heavier than the ISS's (the bright set alone is about 150 satellites), so it runs
 * in full only when an input changes or every [FULL_RECOMPUTE]; in between, passes that
 * are over are dropped every [PRUNE_INTERVAL].
 */
class ObserveSatellitePassesUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val satelliteRepository: SatelliteRepository,
    private val predictor: SatellitePassPredictor,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(observer: GeoCoordinate): Flow<SatelliteForecast> =
        preferences.enabledSatelliteSets
            .flatMapLatest { sets -> satelliteRepository.observeSets(sets - SatelliteSet.ISS) }
            .transformLatest { elementsBySet ->
                var forecast: SatelliteForecast? = null
                while (true) {
                    val now = timeProvider.now()
                    val previous = forecast
                    val next = if (previous != null && Duration.between(previous.computedAt, now) < FULL_RECOMPUTE) {
                        previous.copy(passes = previous.passes.filter { it.pass.end.isAfter(now) })
                    } else {
                        satelliteForecast(predictor, elementsBySet, observer, now)
                    }
                    forecast = next
                    emit(next)
                    delay(PRUNE_INTERVAL.toMillis())
                }
            }
            .flowOn(computeDispatcher)

    companion object {
        val FULL_RECOMPUTE: Duration = Duration.ofHours(1)
        val PRUNE_INTERVAL: Duration = Duration.ofMinutes(5)
    }
}

/** The ground track under a pass, from rise to set, for the map: where on Earth to picture it. */
class PredictPassGroundTrackUseCase @Inject constructor(
    private val predictor: SatellitePassPredictor,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(upcoming: UpcomingPass): List<SatellitePosition> = withContext(computeDispatcher) {
        val pass = upcoming.pass
        predictor.groundTrack(upcoming.satellite.elements, from = pass.start, to = pass.end, step = TRACK_STEP)
    }

    private companion object {
        val TRACK_STEP: Duration = Duration.ofSeconds(15)
    }
}

class ObserveSatellitePassRemindersUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
) {
    operator fun invoke(): Flow<Set<SatelliteSet>> = preferences.satellitePassReminders
}

class SetSatellitePassReminderEnabledUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val rescheduleReminder: RescheduleSatellitePassReminderUseCase,
) {
    suspend operator fun invoke(set: SatelliteSet, enabled: Boolean) {
        if (set !in REMINDABLE_SATELLITE_SETS) return
        preferences.setSatellitePassRemindersEnabled(set, enabled)
        rescheduleReminder()
    }
}

/**
 * Like [RescheduleIssPassReminderUseCase], for every enabled set with its reminder on: one pending
 * wake-up for the soonest visible pass among them, or none when no reminder is on.
 */
class RescheduleSatellitePassReminderUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val locationRepository: ObserverLocationRepository,
    private val satelliteRepository: SatelliteRepository,
    private val predictor: SatellitePassPredictor,
    private val scheduler: SatellitePassReminderScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke() {
        val sets = preferences.remindableSets()
        if (sets.isEmpty()) {
            scheduler.cancel()
            return
        }
        val now = timeProvider.now()
        val next = nextRemindablePass(sets, now)
        scheduler.scheduleAt(
            if (next == null) {
                now.plus(RescheduleIssPassReminderUseCase.RECHECK_INTERVAL)
            } else {
                maxOf(now, next.pass.start.minus(RescheduleIssPassReminderUseCase.REMINDER_LEAD))
            },
        )
    }

    private suspend fun nextRemindablePass(sets: Set<SatelliteSet>, now: Instant): UpcomingPass? {
        val observer = locationRepository.location.first()?.coordinate ?: return null
        val lastReminded = preferences.lastRemindedSatellitePassStart.first()
        val elements = satelliteRepository.observeSets(sets).first()
        return satelliteForecast(predictor, elements, observer, now).passes.firstOrNull { upcoming ->
            upcoming.pass.start.isAfter(now) && (lastReminded == null || upcoming.pass.start.isAfter(lastReminded))
        }
    }
}

/**
 * What the satellite reminder's wake-up runs: freshens the reminded sets when due (best-effort),
 * posts a reminder for each visible pass starting within the lead time - once per pass - and arms
 * the next wake-up.
 */
class RemindOfSatellitePassUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val locationRepository: ObserverLocationRepository,
    private val satelliteRepository: SatelliteRepository,
    private val refreshSatelliteSets: RefreshSatelliteSetsUseCase,
    private val predictor: SatellitePassPredictor,
    private val notifier: SatellitePassNotifier,
    private val rescheduleReminder: RescheduleSatellitePassReminderUseCase,
    private val timeProvider: TimeProvider,
) {
    /** @return the passes a reminder was posted for. */
    suspend operator fun invoke(): List<UpcomingPass> {
        val sets = preferences.remindableSets()
        if (sets.isEmpty()) {
            rescheduleReminder()
            return emptyList()
        }
        refreshSatelliteSets(sets)

        val now = timeProvider.now()
        val passes = imminentPasses(sets, now)
        passes.forEach { notifier.notifyUpcomingPass(it) }
        passes.maxOfOrNull { it.pass.start }?.let { preferences.setLastRemindedSatellitePassStart(it) }
        rescheduleReminder()
        return passes
    }

    private suspend fun imminentPasses(sets: Set<SatelliteSet>, now: Instant): List<UpcomingPass> {
        val observer = locationRepository.location.first()?.coordinate ?: return emptyList()
        val lastReminded = preferences.lastRemindedSatellitePassStart.first()
        val horizon = now.plus(RescheduleIssPassReminderUseCase.REMINDER_LEAD).plus(LATE_WAKE_UP_SLACK)
        val elements = satelliteRepository.observeSets(sets).first()
        return satelliteForecast(predictor, elements, observer, now).passes
            .filter { upcoming ->
                upcoming.pass.end.isAfter(now) &&
                    !upcoming.pass.start.isAfter(horizon) &&
                    (lastReminded == null || upcoming.pass.start.isAfter(lastReminded))
            }
            .distinctBy { it.satellite.noradId }
    }

    private companion object {
        /** WorkManager may run a delayed job a little early or late. */
        val LATE_WAKE_UP_SLACK: Duration = Duration.ofMinutes(5)
    }
}

/** How far ahead each kind of set is searched. */
private val NAMED_FORECAST_WINDOW: Duration = Duration.ofDays(5)

/** About 150 satellites pass every night: the coming night is enough. */
private val BRIGHT_FORECAST_WINDOW: Duration = Duration.ofDays(1)

/** The ISS, Hubble and Tiangong are only listed under their own sets, not again as bright satellites. */
private val NAMED_NORAD_IDS: Set<Int> = SatelliteSet.entries.mapNotNullTo(mutableSetOf()) { it.noradId }

private suspend fun SkyPreferencesRepository.remindableSets(): Set<SatelliteSet> =
    satellitePassReminders.first() intersect enabledSatelliteSets.first() intersect REMINDABLE_SATELLITE_SETS

/** Every set's cached elements, re-emitted whenever one of them changes. */
private fun SatelliteRepository.observeSets(sets: Set<SatelliteSet>): Flow<Map<SatelliteSet, List<OrbitalElements>>> {
    if (sets.isEmpty()) return flowOf(emptyMap())
    val flows = sets.sorted().map { set -> observeSet(set).map { elements -> set to elements } }
    return combine(flows) { pairs -> pairs.toMap() }
}

internal fun satelliteForecast(
    predictor: SatellitePassPredictor,
    elementsBySet: Map<SatelliteSet, List<OrbitalElements>>,
    observer: GeoCoordinate,
    now: Instant,
): SatelliteForecast {
    val status = mutableMapOf<SatelliteSet, SatelliteSetStatus>()
    val passes = elementsBySet.flatMap { (set, cached) ->
        val usable = cached.filterNot { it.isTooOldAt(now) }
        val tracked = trackedSatellites(set, usable, now)
        status[set] = when {
            cached.isEmpty() -> SatelliteSetStatus.NO_DATA
            usable.isEmpty() -> SatelliteSetStatus.STALE
            tracked.isEmpty() && set == SatelliteSet.STARLINK_TRAINS -> SatelliteSetStatus.NO_RECENT_LAUNCH
            else -> SatelliteSetStatus.READY
        }
        val window = if (set == SatelliteSet.BRIGHT_SATELLITES) BRIGHT_FORECAST_WINDOW else NAMED_FORECAST_WINDOW
        val found = predictor.visiblePasses(tracked.map { it.elements }, observer, from = now, to = now.plus(window))
        tracked.flatMap { satellite -> found[satellite.noradId].orEmpty().map { UpcomingPass(satellite, it) } }
    }
    return SatelliteForecast(
        enabledSets = elementsBySet.keys,
        passes = passes.sortedBy { it.pass.start },
        setStatus = status,
        computedAt = now,
    )
}

private fun trackedSatellites(set: SatelliteSet, usable: List<OrbitalElements>, now: Instant): List<TrackedSatellite> =
    when (set) {
        SatelliteSet.STARLINK_TRAINS -> StarlinkTrains.from(usable, now)
        SatelliteSet.BRIGHT_SATELLITES ->
            usable.filter { it.noradId !in NAMED_NORAD_IDS }.map { TrackedSatellite(set, it) }
        else -> usable.filter { it.noradId == set.noradId }.map { TrackedSatellite(set, it) }
    }
