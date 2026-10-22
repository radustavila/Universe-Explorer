package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.CloudForecastRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.DarkSkyCalculator
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertNotifier
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertScheduler
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingScorer
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.DarkNight
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.StargazingAlert
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import com.radustavila.nasaexplorer.core.model.StargazingForecast
import com.radustavila.nasaexplorer.core.model.StargazingNight
import com.radustavila.nasaexplorer.core.model.SyncSource
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformLatest

/**
 * Tonight and the next nights at [observer], scored from the cached cloud forecast. Recomputed when
 * a new forecast is stored and every [RECOMPUTE_INTERVAL], so hours that have gone by drop out and
 * the outlook rolls over to the next night on its own. Never touches the network.
 */
class ObserveStargazingForecastUseCase @Inject constructor(
    private val repository: CloudForecastRepository,
    private val calculator: DarkSkyCalculator,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(observer: GeoCoordinate, nights: Int = NIGHTS): Flow<StargazingForecast> =
        repository.observeForecast(observer).transformLatest { forecast ->
            while (true) {
                val now = timeProvider.now()
                emit(
                    StargazingForecast(
                        coordinate = observer,
                        nights = calculator.nights(observer, now, nights)
                            .map { night -> StargazingScorer.score(night, forecast, now) },
                        fetchedAt = forecast?.fetchedAt,
                    ),
                )
                delay(RECOMPUTE_INTERVAL.toMillis())
            }
        }.flowOn(computeDispatcher)

    companion object {
        /** Tonight and the next four. */
        const val NIGHTS = 5
        val RECOMPUTE_INTERVAL: Duration = Duration.ofMinutes(10)
    }
}

/**
 * Downloads the cloud forecast for [observer] through [RefreshSourceUseCase] as
 * [SyncSource.MET_NORWAY]. There is no interval and no force: the repository goes to the network
 * only once MET Norway's `Expires` has passed, as its terms ask, and a rate-limited failure still
 * holds the source back until `Retry-After`.
 */
class RefreshCloudForecastUseCase @Inject constructor(
    private val repository: CloudForecastRepository,
    private val refreshSource: RefreshSourceUseCase,
) {
    suspend operator fun invoke(observer: GeoCoordinate): SourceRefreshResult<Unit> =
        refreshSource(SyncSource.MET_NORWAY) { repository.refreshForecast(observer) }
}

class ObserveStargazingAlertSettingsUseCase @Inject constructor(
    private val repository: StargazingAlertRepository,
) {
    operator fun invoke(): Flow<StargazingAlertSettings> = repository.settings
}

/**
 * Decides whether to announce a clear night, from the cached forecast alone (no network).
 *
 * Only tonight counts (the night under way, or the coming one while the Sun is up): it must reach
 * the user's minimum score and still have [MIN_REMAINING] of its clear window ahead. Each night is
 * announced at most once; later checks that night stay silent.
 */
class CheckStargazingAlertUseCase @Inject constructor(
    private val alertRepository: StargazingAlertRepository,
    private val locationRepository: ObserverLocationRepository,
    private val forecastRepository: CloudForecastRepository,
    private val calculator: DarkSkyCalculator,
    private val notifier: StargazingAlertNotifier,
    private val timeProvider: TimeProvider,
) {
    /** @return the alert that was posted, or null when none was due. */
    suspend operator fun invoke(): StargazingAlert? {
        val (night, alert) = dueAlert() ?: return null
        notifier.notifyClearNight(alert)
        alertRepository.setAlertedUntil(night.sunrise ?: night.darkEnd ?: alert.window.end)
        return alert
    }

    /** Tonight, scored, when the alert is on, a place is saved and tonight was not announced yet. */
    private suspend fun unannouncedTonight(): Pair<DarkNight, StargazingNight>? {
        val settings = alertRepository.settings.first()
        val observer = locationRepository.location.first()?.coordinate?.takeIf { settings.enabled }
        val forecast = observer?.let { forecastRepository.observeForecast(it).first() }
        val now = timeProvider.now()
        val night = if (observer != null && forecast != null) {
            calculator.nights(observer, now, count = 1).firstOrNull()
        } else {
            null
        }
        val alertedUntil = alertRepository.alertedUntil()
        val announced = alertedUntil != null && night?.darkStart?.isBefore(alertedUntil) == true
        return night?.takeIf { it.darkStart != null && !announced }
            ?.let { it to StargazingScorer.score(it, forecast, now).withMinimum(settings.minimumScore) }
    }

    private suspend fun dueAlert(): Pair<DarkNight, StargazingAlert>? {
        val (night, tonight) = unannouncedTonight() ?: return null
        val score = tonight.score
        val window = tonight.bestWindow?.takeIf { it.end.isAfter(timeProvider.now().plus(MIN_REMAINING)) }
        return if (score != null && window != null) {
            night to StargazingAlert(score, window, moonIlluminatedFraction = night.moonIlluminatedFraction)
        } else {
            null
        }
    }

    /** The night with its score dropped when below [minimum], so it cannot alert. */
    private fun StargazingNight.withMinimum(minimum: Int): StargazingNight =
        if ((score ?: 0) >= minimum) this else copy(score = null)

    companion object {
        /** Too little left to be worth getting the telescope out. */
        val MIN_REMAINING: Duration = Duration.ofMinutes(30)
    }
}

/**
 * The background check behind the alert: refreshes the forecast for the saved place (MET Norway's
 * `Expires` decides whether anything is sent), then checks tonight. Does nothing while the alert
 * is off or no place is saved, so no request is made for a user who never asked for it.
 */
class RunStargazingAlertCheckUseCase @Inject constructor(
    private val alertRepository: StargazingAlertRepository,
    private val locationRepository: ObserverLocationRepository,
    private val refreshCloudForecast: RefreshCloudForecastUseCase,
    private val checkStargazingAlert: CheckStargazingAlertUseCase,
) {
    /** @return the refresh's outcome, or null when nothing was refreshed. */
    suspend operator fun invoke(): SourceRefreshResult<Unit>? {
        val enabled = alertRepository.settings.first().enabled
        val observer = locationRepository.location.first()?.coordinate?.takeIf { enabled } ?: return null
        val outcome = refreshCloudForecast(observer)
        // A failed refresh still leaves the cached forecast to check.
        checkStargazingAlert()
        return outcome
    }
}

/**
 * Turns the clear-night alert on or off. On, it registers the background check (whose first run
 * comes at once) and checks the cached forecast straight away; off, it cancels the check, which
 * nothing else needs.
 */
class SetStargazingAlertEnabledUseCase @Inject constructor(
    private val repository: StargazingAlertRepository,
    private val scheduler: StargazingAlertScheduler,
    private val checkStargazingAlert: CheckStargazingAlertUseCase,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setEnabled(enabled)
        if (enabled) {
            scheduler.ensurePeriodicCheck()
            checkStargazingAlert()
        } else {
            scheduler.cancelPeriodicCheck()
        }
    }
}

class SetStargazingAlertMinimumScoreUseCase @Inject constructor(
    private val repository: StargazingAlertRepository,
) {
    suspend operator fun invoke(score: Int) {
        require(score in 0..MAX_SCORE) { "a score is 0–100" }
        repository.setMinimumScore(score)
    }

    private companion object {
        const val MAX_SCORE = 100
    }
}

/** Brings the background check in line with the alert setting at app start. */
class ScheduleStargazingAlertUseCase @Inject constructor(
    private val repository: StargazingAlertRepository,
    private val scheduler: StargazingAlertScheduler,
) {
    suspend operator fun invoke() {
        if (repository.settings.first().enabled) scheduler.ensurePeriodicCheck() else scheduler.cancelPeriodicCheck()
    }
}
