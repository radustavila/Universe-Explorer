package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.sky.IssPassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.IssPassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.model.OrbitalElements.Companion.ISS_NORAD_ID
import com.radustavila.nasaexplorer.core.model.SatellitePass
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveIssPassRemindersEnabledUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = preferences.issPassRemindersEnabled
}

class SetIssPassRemindersEnabledUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val rescheduleReminder: RescheduleIssPassReminderUseCase,
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferences.setIssPassRemindersEnabled(enabled)
        rescheduleReminder()
    }
}

/**
 * Arms the one pending wake-up for the next visible ISS pass, [REMINDER_LEAD] before it starts, or
 * cancels it when reminders are off or there is nothing to predict from. Called whenever an input
 * changes (toggle, location, new elements) and after every reminder.
 *
 * With no visible pass in the forecast window it wakes up again after [RECHECK_INTERVAL], by which
 * time newer elements may show one.
 */
class RescheduleIssPassReminderUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val locationRepository: ObserverLocationRepository,
    private val satelliteRepository: SatelliteRepository,
    private val predictor: SatellitePassPredictor,
    private val scheduler: IssPassReminderScheduler,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke() {
        if (!preferences.issPassRemindersEnabled.first()) {
            scheduler.cancel()
            return
        }
        val now = timeProvider.now()
        val next = nextRemindablePass(now)
        scheduler.scheduleAt(
            if (next == null) now.plus(RECHECK_INTERVAL) else maxOf(now, next.start.minus(REMINDER_LEAD)),
        )
    }

    private suspend fun nextRemindablePass(now: Instant): SatellitePass? {
        val observer = locationRepository.location.first()?.coordinate ?: return null
        val elements = satelliteRepository.observeElements(ISS_NORAD_ID).first()
            ?.takeUnless { it.isTooOldAt(now) } ?: return null
        val lastReminded = preferences.lastRemindedPassStart.first()
        return predictor.passes(elements, observer, from = now, to = now.plus(FORECAST_WINDOW))
            .firstOrNull { pass ->
                pass.isVisible && pass.start.isAfter(now) && (lastReminded == null || pass.start.isAfter(lastReminded))
            }
    }

    companion object {
        val REMINDER_LEAD: Duration = Duration.ofMinutes(10)
        val FORECAST_WINDOW: Duration = Duration.ofDays(3)
        val RECHECK_INTERVAL: Duration = Duration.ofHours(12)
    }
}

/**
 * What the reminder's wake-up runs: freshens the elements when due (best-effort), posts the
 * reminder for a visible pass starting within the lead time - once per pass - and arms the next
 * wake-up.
 */
class RemindOfIssPassUseCase @Inject constructor(
    private val preferences: SkyPreferencesRepository,
    private val locationRepository: ObserverLocationRepository,
    private val satelliteRepository: SatelliteRepository,
    private val refreshOrbitalElements: RefreshOrbitalElementsUseCase,
    private val predictor: SatellitePassPredictor,
    private val notifier: IssPassNotifier,
    private val rescheduleReminder: RescheduleIssPassReminderUseCase,
    private val timeProvider: TimeProvider,
) {
    /** @return the pass a reminder was posted for, if any. */
    suspend operator fun invoke(): SatellitePass? {
        if (!preferences.issPassRemindersEnabled.first()) {
            rescheduleReminder()
            return null
        }
        refreshOrbitalElements()

        val now = timeProvider.now()
        val pass = imminentPass(now)
        if (pass != null) {
            notifier.notifyUpcomingPass(pass)
            preferences.setLastRemindedPassStart(pass.start)
        }
        rescheduleReminder()
        return pass
    }

    private suspend fun imminentPass(now: Instant): SatellitePass? {
        val observer = locationRepository.location.first()?.coordinate ?: return null
        val elements = satelliteRepository.observeElements(ISS_NORAD_ID).first()
            ?.takeUnless { it.isTooOldAt(now) } ?: return null
        val lastReminded = preferences.lastRemindedPassStart.first()
        val horizon = now.plus(RescheduleIssPassReminderUseCase.REMINDER_LEAD).plus(LATE_WAKE_UP_SLACK)
        return predictor.passes(elements, observer, from = now, to = horizon.plus(MAX_PASS_DURATION))
            .firstOrNull { pass ->
                pass.isVisible &&
                    pass.end.isAfter(now) &&
                    !pass.start.isAfter(horizon) &&
                    (lastReminded == null || pass.start.isAfter(lastReminded))
            }
    }

    private companion object {
        /** WorkManager may run a delayed job a little early or late. */
        val LATE_WAKE_UP_SLACK: Duration = Duration.ofMinutes(5)
        val MAX_PASS_DURATION: Duration = Duration.ofMinutes(15)
    }
}
