package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.announcement.AuroraAlertNotifier
import com.radustavila.nasaexplorer.core.domain.repository.AuroraAlertRepository
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.model.AuroraAlert
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveAuroraAlertSettingsUseCase @Inject constructor(
    private val repository: AuroraAlertRepository,
) {
    operator fun invoke(): Flow<AuroraAlertSettings> = repository.settings
}

/**
 * Decides whether to alert, from the cached Kp forecast alone (no network).
 *
 * Alerts are opt-in and need the user's place. A forecast slot in the next [LOOKAHEAD] whose Kp
 * reaches the level visible from there makes a storm; the storm is announced once. Later syncs that
 * still forecast it (even with its start or end moved) stay silent, because it begins before the
 * end of the period already announced. Only a storm starting after that period alerts again.
 */
class CheckAuroraAlertUseCase @Inject constructor(
    private val alertRepository: AuroraAlertRepository,
    private val spaceWeatherRepository: SpaceWeatherRepository,
    private val notifier: AuroraAlertNotifier,
    private val timeProvider: TimeProvider,
) {
    /** @return the alert that was posted, or null when none was due. */
    suspend operator fun invoke(): AuroraAlert? {
        val settings = alertRepository.settings.first()
        if (!settings.enabled) return null
        val threshold = settings.minimumKp ?: return null

        val now = timeProvider.now()
        val horizon = now.plus(LOOKAHEAD)
        val storm = spaceWeatherRepository.observeKpForecast().first()
            .filter { it.end.isAfter(now) && it.start.isBefore(horizon) && it.level >= threshold }
            .sortedBy { it.start }
        if (storm.isEmpty()) return null

        val stormEnd = storm.last().end
        val alertedUntil = alertRepository.alertedUntil()
        if (alertedUntil != null && !storm.first().start.isAfter(alertedUntil)) {
            // The storm already announced, still forecast: remember how long it now lasts.
            if (stormEnd.isAfter(alertedUntil)) alertRepository.setAlertedUntil(stormEnd)
            return null
        }

        val alert = AuroraAlert(
            startsAt = maxOf(storm.first().start, now),
            expectedKp = storm.maxOf { it.kp },
            threshold = threshold,
        )
        notifier.notifyAuroraAlert(alert)
        alertRepository.setAlertedUntil(stormEnd)
        return alert
    }

    companion object {
        /** Further ahead the forecast is too uncertain to get someone out of bed for. */
        val LOOKAHEAD: Duration = Duration.ofHours(24)
    }
}

/**
 * Turns alerts on or off. Turning them on starts the 30-minute Kp refresh and checks the cached
 * forecast at once, so a storm already forecast is announced without waiting for a sync; turning
 * them off stops the refresh, which nothing else needs.
 */
class SetAuroraAlertEnabledUseCase @Inject constructor(
    private val repository: AuroraAlertRepository,
    private val scheduler: SpaceWeatherSyncScheduler,
    private val checkAuroraAlert: CheckAuroraAlertUseCase,
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setEnabled(enabled)
        // A fresh start: the next storm is announced even if it was already forecast.
        repository.setAlertedUntil(null)
        if (enabled) {
            scheduler.ensurePeriodicSync()
            checkAuroraAlert()
        } else {
            scheduler.cancelPeriodicSync()
        }
    }
}

/**
 * Brings the 30-minute Kp refresh in line with the alert setting, at app start: registered while
 * alerts are on, cancelled otherwise. Older versions registered it whenever the space weather
 * screen opened, so installs upgraded from them may hold a job that nothing needs.
 */
class ScheduleAuroraAlertRefreshUseCase @Inject constructor(
    private val repository: AuroraAlertRepository,
    private val scheduler: SpaceWeatherSyncScheduler,
) {
    suspend operator fun invoke() {
        if (repository.settings.first().enabled) scheduler.ensurePeriodicSync() else scheduler.cancelPeriodicSync()
    }
}

/** Saves where the user watches from; a new place may change which storms reach it. */
class SetAuroraAlertLocationUseCase @Inject constructor(
    private val repository: AuroraAlertRepository,
    private val checkAuroraAlert: CheckAuroraAlertUseCase,
) {
    suspend operator fun invoke(latitude: Double, longitude: Double) {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "not a place on Earth" }
        repository.setLocation(latitude, longitude)
        repository.setAlertedUntil(null)
        checkAuroraAlert()
    }
}
