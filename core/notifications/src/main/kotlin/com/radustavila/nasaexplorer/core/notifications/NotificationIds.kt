package com.radustavila.nasaexplorer.core.notifications

import com.radustavila.nasaexplorer.core.model.SatelliteSet

/**
 * Every notification id and content-intent request code the app posts with, in one place so no two
 * notifiers share one: a notification posted with another feature's id (and tag) silently replaces
 * that feature's unread one. `NotificationIdsTest` checks they stay distinct.
 *
 * Ids are stable across releases: changing one orphans a notification already in the tray, which
 * the next post then fails to replace. Add new ones; don't renumber.
 */
internal object NotificationIds {

    const val NEW_APOD = 1
    const val ISS_PASS = 2
    const val AURORA_ALERT = 3

    /** Posted with the event's id as tag, so each event's reminder stands alone. */
    const val SKY_EVENT = 4

    /** Posted with [STARGAZING_TAG]: tonight's alert replaces last night's unread one. */
    const val STARGAZING_ALERT = 5
    const val STARGAZING_TAG = "stargazing"

    /** Posted with [EARTHQUAKE_TAG]: a newer strong quake replaces an older unread one. */
    const val EARTHQUAKE_ALERT = 6
    const val EARTHQUAKE_TAG = "earthquake"

    /** Posted with the launch's id as tag, so each launch's reminder stands alone. */
    const val LAUNCH_REMINDER = 7

    /** Posted with [QUIZ_TAG]: today's reminder replaces yesterday's unread one. */
    const val QUIZ_REMINDER = 8
    const val QUIZ_TAG = "quiz"

    /** One id per [SatelliteSet] from here up ([satellitePass]), clear of the ids above. */
    const val SATELLITE_PASS_BASE = 40

    fun satellitePass(set: SatelliteSet): Int = SATELLITE_PASS_BASE + set.ordinal

    /**
     * Request codes of the notifications' content intents. A pending intent is shared by equal
     * intents with the same code, so a shared code could retarget another notification's tap.
     * Sky events and launch reminders use the id's hash instead (one per event or launch).
     */
    object RequestCodes {
        const val NEW_APOD = 0
        const val AURORA_ALERT = 1
        const val ISS_PASS = 2
        const val SATELLITE_PASS = 4
        const val STARGAZING_ALERT = 11
        const val EARTHQUAKE_ALERT = 12
        const val QUIZ_REMINDER = 13
    }
}
