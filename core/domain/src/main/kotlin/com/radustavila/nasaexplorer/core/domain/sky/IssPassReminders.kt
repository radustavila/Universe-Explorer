package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatelliteSet
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

    /** The satellites besides the ISS whose passes are listed; none until the user picks some. */
    val enabledSatelliteSets: Flow<Set<SatelliteSet>>

    suspend fun setSatelliteSetEnabled(set: SatelliteSet, enabled: Boolean)

    /** Sets with reminders on (opt-in, like the ISS's); only counted while the set is enabled. */
    val satellitePassReminders: Flow<Set<SatelliteSet>>

    suspend fun setSatellitePassRemindersEnabled(set: SatelliteSet, enabled: Boolean)

    /** Start of the last satellite pass (not the ISS) a reminder was posted for. */
    val lastRemindedSatellitePassStart: Flow<Instant?>

    suspend fun setLastRemindedSatellitePassStart(start: Instant)
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

/** Like [IssPassReminderScheduler], for the other satellites' reminders: a separate wake-up. */
interface SatellitePassReminderScheduler {

    /** Replaces any pending wake-up with one at [at]. */
    fun scheduleAt(at: Instant)

    fun cancel()
}

/** Posts "Hubble passes over you in a few minutes", one notification per set. */
interface SatellitePassNotifier {
    suspend fun notifyUpcomingPass(pass: UpcomingPass)
}
