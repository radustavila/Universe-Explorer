package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.Earthquake
import com.radustavila.nasaexplorer.core.model.EarthquakeAlert
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.model.distanceKmTo
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Cached quakes that at least one [EarthquakeFilter] shows, newest first: a quake that left both
 * feeds' windows while the app was offline is not "recent" any more.
 */
class ObserveEarthquakesUseCase @Inject constructor(
    private val repository: EarthquakeRepository,
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(): Flow<List<Earthquake>> = repository.observeEarthquakes().map { quakes ->
        val now = timeProvider.now()
        quakes.filter { quake -> EarthquakeFilter.entries.any { it.matches(quake, now) } }
    }
}

/**
 * Refreshes the quake feeds at most once per [MIN_REFRESH_INTERVAL] (USGS publishes no limit and
 * updates every minute; `If-Modified-Since` keeps repeats cheap), then checks the strong-quake
 * alert against what is cached. Runs from the periodic events sync and when a map with quakes opens.
 */
class RefreshEarthquakesUseCase @Inject constructor(
    private val repository: EarthquakeRepository,
    private val refreshSource: RefreshSourceUseCase,
    private val checkEarthquakeAlert: CheckEarthquakeAlertUseCase,
) {
    suspend operator fun invoke(force: Boolean = false): SourceRefreshResult<Int> {
        val outcome = refreshSource(SyncSource.EARTHQUAKES, minInterval = MIN_REFRESH_INTERVAL, force = force) {
            repository.refreshEarthquakes()
        }
        if (outcome is SourceRefreshResult.Completed && outcome.result is DataResult.Success) checkEarthquakeAlert()
        return outcome
    }

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}

/**
 * Decides whether to announce a quake, from the cache and the saved place alone: the place never
 * leaves the phone. A quake alerts when it is at least [MIN_MAGNITUDE] within [NEARBY_RADIUS_KM], or
 * carries USGS's tsunami flag within [TSUNAMI_RADIUS_KM]; only quakes of the last [MAX_AGE] that
 * were not announced yet count, and of several the strongest is posted.
 */
class CheckEarthquakeAlertUseCase @Inject constructor(
    private val alertRepository: EarthquakeAlertRepository,
    private val locationRepository: ObserverLocationRepository,
    private val earthquakeRepository: EarthquakeRepository,
    private val notifier: EarthquakeAlertNotifier,
    private val timeProvider: TimeProvider,
) {
    /** @return the alert that was posted, or null when none was due. */
    suspend operator fun invoke(): EarthquakeAlert? {
        if (!alertRepository.enabled.first()) return null
        val place = locationRepository.location.first()?.coordinate ?: return null
        val now = timeProvider.now()
        val alertedUntil = alertRepository.alertedUntil()
        val due = earthquakeRepository.observeEarthquakes().first()
            .filter { quake -> !quake.time.isBefore(now.minus(MAX_AGE)) }
            .filter { quake -> alertedUntil == null || quake.time.isAfter(alertedUntil) }
            .mapNotNull { quake ->
                val distance = place.distanceKmTo(quake.position)
                val nearby = quake.magnitude >= MIN_MAGNITUDE && distance <= NEARBY_RADIUS_KM
                val tsunami = quake.tsunami && distance <= TSUNAMI_RADIUS_KM
                if (nearby || tsunami) EarthquakeAlert(quake, distance, isNearby = nearby) else null
            }
        val alert = due.maxByOrNull { it.earthquake.magnitude } ?: return null
        notifier.notifyEarthquake(alert)
        alertRepository.setAlertedUntil(due.maxOf { it.earthquake.time })
        return alert
    }

    companion object {
        const val MIN_MAGNITUDE = 5.0
        const val NEARBY_RADIUS_KM = 500.0

        /** Far enough for a regional tsunami to reach the coast within hours. */
        const val TSUNAMI_RADIUS_KM = 2_000.0

        /** Older news is not an alert; the background sync runs every few hours. */
        val MAX_AGE: Duration = Duration.ofHours(12)
    }
}

class ObserveEarthquakeAlertEnabledUseCase @Inject constructor(
    private val repository: EarthquakeAlertRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.enabled
}

/**
 * Turns the strong-quake alert on or off. On, it checks the cached quakes at once, so a strong
 * quake of the last hours is announced without waiting for the next sync. The background refresh
 * runs anyway (it feeds the maps), so there is nothing to schedule.
 */
class SetEarthquakeAlertEnabledUseCase @Inject constructor(
    private val repository: EarthquakeAlertRepository,
    private val checkEarthquakeAlert: CheckEarthquakeAlertUseCase,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setEnabled(enabled)
        if (enabled) checkEarthquakeAlert()
    }
}
