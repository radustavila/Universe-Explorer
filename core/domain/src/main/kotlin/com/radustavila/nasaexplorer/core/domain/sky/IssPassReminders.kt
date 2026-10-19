package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.SatellitePass
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The user's Sky tonight choices. */
interface SkyPreferencesRepository {

    /** Opt-in: no reminder is ever posted until the user turns this on. */
    val issPassRemindersEnabled: Flow<Boolean>

    suspend fun setIssPassRemindersEnabled(enabled: Boolean)

    /** Start of the last pass a reminder was posted for, so a pass is announced once. */
    val lastRemindedPassStart: Flow<Instant?>

    suspend fun setLastRemindedPassStart(start: Instant)
}

/** Wakes the app shortly before the next visible pass, even when it is not running. */
interface IssPassReminderScheduler {

    /** Replaces any pending wake-up with one at [at]. */
    fun scheduleAt(at: Instant)

    fun cancel()
}

/** Posts "the ISS passes over you in a few minutes". */
interface IssPassNotifier {
    suspend fun notifyUpcomingPass(pass: SatellitePass)
}
