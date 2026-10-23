package com.radustavila.nasaexplorer.core.model

import java.time.Duration
import java.time.Instant

/**
 * One prime visit in the James Webb Space Telescope's weekly observing schedule (F24), as STScI
 * published it. The schedule is a plan, not a log: visits can move or drop out, so screens word
 * these as "scheduled".
 *
 * [instrument], [targetName], [category] and [keywords] are STScI's own words ("NIRSpec MultiObject
 * Spectroscopy", "WASP-39", "Star", "Exoplanet Systems"); the UI words the categories it knows.
 */
data class WebbVisit(
    /** STScI's `program:observation:visit` id, e.g. "1366:4:1". */
    val visitId: String,
    val start: Instant,
    val duration: Duration,
    /** "PRIME TARGETED FIXED", "PRIME UNTARGETED", "PRIME WFSC ROUTINE", … */
    val visitType: String,
    val instrument: String,
    val targetName: String? = null,
    val category: String? = null,
    val keywords: String? = null,
    /** Instruments observing at the same time (coordinated or attached parallels), in file order. */
    val parallels: List<String> = emptyList(),
) {
    val end: Instant get() = start + duration

    /** The program number, the part of [visitId] before the first colon. */
    val program: String get() = visitId.substringBefore(':')

    /**
     * Calibration and engineering visits: calibration targets, and every visit that is not a
     * targeted one (darks, wavefront sensing, momentum unloads, station keeping). Shown as
     * "Calibration", never hidden, so the schedule has no holes while Webb looks after itself.
     */
    val isCalibration: Boolean
        get() = category.equals(CALIBRATION_CATEGORY, ignoreCase = true) ||
            targetName.isNullOrBlank() ||
            visitType.split(' ').none { it.equals(TARGETED_TYPE, ignoreCase = true) }

    /**
     * What to search the image library for: the target's name with its hyphens as spaces
     * ("NGC-1333" → "NGC 1333"). Null for calibration visits and for planning labels such as
     * "input_lulls_00" or "o16" (an underscore, or no capital letter), which name no object.
     */
    val searchQuery: String?
        get() = targetName
            ?.takeUnless { isCalibration || '_' in it || it.none(Char::isUpperCase) }
            ?.replace(SEPARATORS, " ")
            ?.trim()

    /** Visits of one program, target and instrument in a row read as one in "up next". */
    fun repeats(other: WebbVisit): Boolean =
        program == other.program && targetName == other.targetName && instrument == other.instrument

    fun isOnAt(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    private companion object {
        const val CALIBRATION_CATEGORY = "Calibration"
        const val TARGETED_TYPE = "TARGETED"
        val SEPARATORS = Regex("-+")
    }
}

/** The newest downloaded schedule: every prime visit, in start order, and when it was downloaded. */
data class WebbSchedule(
    val visits: List<WebbVisit>,
    val fetchedAt: Instant,
) {
    val firstStart: Instant? get() = visits.firstOrNull()?.start
    val lastEnd: Instant? get() = visits.maxOfOrNull { it.end }
}

/**
 * What the schedule says for one moment: the visit on now, or the gap Webb is in (slewing between
 * targets or idle), and the next visits.
 */
data class WebbNow(
    val now: Instant,
    /** The visit scheduled at [now], or null in a gap or outside the schedule. */
    val current: WebbVisit?,
    /** The last visit that ended before [now], when [current] is null. */
    val previous: WebbVisit?,
    /**
     * The visits after [now] (or after [current]), soonest first, at most [UP_NEXT]; a visit that
     * [WebbVisit.repeats] the one before it is left out.
     */
    val upNext: List<WebbVisit>,
    /** When the schedule was downloaded. */
    val fetchedAt: Instant,
    /** The schedule's first visit started more than [STALE_AFTER] ago: a newer week is missing. */
    val isStale: Boolean,
    /** When the schedule's first visit started, for the stale note. */
    val scheduleStart: Instant,
) {
    val gap: WebbGap?
        get() = when {
            current != null -> null
            upNext.isEmpty() -> WebbGap.SCHEDULE_ENDED
            previous == null -> WebbGap.NOT_STARTED
            Duration.between(previous.end, upNext.first().start) <= SLEW_GAP -> WebbGap.SLEWING
            else -> WebbGap.IDLE
        }

    companion object {
        const val UP_NEXT = 3

        /** A week's schedule plus a day of grace; older than this the schedule is out of date. */
        val STALE_AFTER: Duration = Duration.ofDays(8)

        /** Gaps up to this long are the slew to the next target; longer ones list the next start. */
        val SLEW_GAP: Duration = Duration.ofMinutes(30)
    }
}

/** Why no visit is scheduled at the moment. */
enum class WebbGap {
    /** Turning to the next target. */
    SLEWING,

    /** A longer pause in the plan. */
    IDLE,

    /** The schedule's first visit is still ahead. */
    NOT_STARTED,

    /** Every visit in the downloaded schedule is over. */
    SCHEDULE_ENDED,
}
