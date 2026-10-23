package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * A rocket launch, as listed by Launch Library 2: upcoming ones for the next-launches card and the
 * Launches screen, past ones for the screen's Past tab.
 *
 * Launches stay a small side section of the app (one card, one screen): only what those show is
 * modelled here.
 */
data class Launch(
    /** Launch Library 2 identifier (a UUID). */
    val id: String,
    val name: String,
    /** "No earlier than": the current target time. It moves as the launch date firms up. */
    val net: Instant,
    val status: LaunchStatus,
    /** The rocket's full name, e.g. "Falcon 9 Block 5". */
    val rocket: String,
    val pad: String,
    /** Who flies it, e.g. "SpaceX"; null when Launch Library 2 does not say. */
    val provider: String? = null,
    val imageUrl: String? = null,
    /** First livestream listed for the launch, if any is announced yet. */
    val livestreamUrl: String? = null,
    /** Public web page about the launch, opened when there is no livestream. */
    val pageUrl: String,
    /** How exact [net] is: to the second on launch day, only a quarter or a year months ahead. */
    val netPrecision: NetPrecision = NetPrecision.UNKNOWN,
    /** The launch window, when announced; [net] lies inside it. */
    val windowStart: Instant? = null,
    val windowEnd: Instant? = null,
    /** Chance of acceptable weather, in percent (0–100), when a forecast is out. */
    val probability: Int? = null,
    val mission: LaunchMission? = null,
    /** Where the pad is, for the detail page's map; null when Launch Library 2 has no position. */
    val padPosition: GeoCoordinate? = null,
    /** The rocket family's name ("Falcon 9") and variant ("Block 5"), when listed separately. */
    val rocketName: String? = null,
    val rocketVariant: String? = null,
    /** Reusable first stages (boosters) and whether and where they land. Empty when not reported. */
    val boosters: List<BoosterStage> = emptyList(),
    /** Streams and recordings in Launch Library 2's priority order (official ones first). */
    val webcasts: List<Webcast> = emptyList(),
    /** People on board; 0 for an uncrewed launch. */
    val crewCount: Int = 0,
    /** Why a launch failed, in Launch Library 2's words; null unless it did. */
    val failReason: String? = null,
    /** Whether the launch comes from the upcoming or the previous list. */
    val timeline: LaunchTimeline = LaunchTimeline.UPCOMING,
) {
    /** Where tapping the launch leads: the livestream when there is one, the launch page otherwise. */
    val linkUrl: String get() = livestreamUrl ?: pageUrl

    val isCrewed: Boolean get() = crewCount > 0
}

/** Which of Launch Library 2's lists a cached launch came from. */
enum class LaunchTimeline { UPCOMING, PAST }

/** What a launch carries and where to. */
data class LaunchMission(
    val name: String?,
    val description: String?,
    /** Launch Library 2's category, e.g. "Communications", "Human Exploration". */
    val type: String?,
    /** Target orbit's name ("Low Earth Orbit") and abbreviation ("LEO"). */
    val orbitName: String?,
    val orbitAbbrev: String?,
)

/** One reusable first stage (or a Falcon Heavy side booster) and its landing. */
data class BoosterStage(
    /** The booster's serial, e.g. "B1067"; null while not assigned. */
    val serial: String?,
    /** Which flight of this booster the launch is (1 = new). */
    val flightNumber: Int?,
    /** "Core", "Strap-on booster", … in Launch Library 2's words. */
    val type: String?,
    val landing: BoosterLanding?,
)

data class BoosterLanding(
    /** False for an expendable launch; null when not decided yet. */
    val attempt: Boolean?,
    /** Null until the launch has happened (or when no landing was attempted). */
    val success: Boolean?,
    /** Where it lands, e.g. "Of Course I Still Love You" / "OCISLY". */
    val locationName: String?,
    val locationAbbrev: String?,
    /** How: "Autonomous Spaceport Drone Ship" / "ASDS", "Return to Launch Site" / "RTLS". */
    val typeName: String?,
    val typeAbbrev: String?,
)

/** A livestream or recording of a launch. Third-party content: linked to, never re-hosted. */
data class Webcast(
    val url: String,
    val title: String?,
    /** Who streams it, e.g. "SpaceX", "NASASpaceflight". */
    val publisher: String?,
    val isLive: Boolean = false,
)

/**
 * How exact a launch's NET is, by Launch Library 2's precision ids: a launch months away is only
 * known to the month or quarter, while one on the pad is known to the second.
 */
enum class NetPrecision {
    SECOND,
    MINUTE,
    HOUR,
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    HALF_YEAR,
    YEAR,
    UNKNOWN,
    ;

    /** Exact enough to count down to and to remind before. */
    val isExact: Boolean get() = this == SECOND || this == MINUTE || this == HOUR || this == UNKNOWN

    companion object {
        /** Launch Library 2's ids: 0 second … 14 year; morning and afternoon (3, 4) count as a day. */
        @Suppress("MagicNumber")
        fun fromId(id: Int?): NetPrecision = when (id) {
            0 -> SECOND
            1 -> MINUTE
            2 -> HOUR
            3, 4, 5 -> DAY
            6 -> WEEK
            7 -> MONTH
            in 8..11 -> QUARTER
            12, 13 -> HALF_YEAR
            14, 15 -> YEAR
            else -> UNKNOWN
        }

        /** The id [fromId] maps back to this precision, for storage. */
        @Suppress("MagicNumber")
        fun NetPrecision.toId(): Int? = when (this) {
            SECOND -> 0
            MINUTE -> 1
            HOUR -> 2
            DAY -> 5
            WEEK -> 6
            MONTH -> 7
            QUARTER -> 8
            HALF_YEAR -> 12
            YEAR -> 14
            UNKNOWN -> null
        }
    }
}

/** Launch Library 2 launch statuses, keyed by their stable numeric ids. */
enum class LaunchStatus(val id: Int) {
    GO(1),
    TBD(2),
    SUCCESS(3),
    FAILURE(4),
    HOLD(5),
    IN_FLIGHT(6),
    PARTIAL_FAILURE(7),
    TBC(8),
    UNKNOWN(-1),
    ;

    /** Set for lift-off: a reminder fires only for these, so a hold or a scrub silences it. */
    val isFirm: Boolean get() = this == GO || this == TBC

    companion object {
        fun fromId(id: Int?): LaunchStatus = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

/**
 * In-app addresses of the Launches screen and of one launch, shared by the navigation graph (which
 * resolves them), the launcher shortcut and the reminder notification (which open them).
 */
object LaunchDeepLink {
    const val LIST_URI = "nasaexplorer://launches"
    const val BASE_URI = "nasaexplorer://launch"

    fun uriFor(launchId: String): String = "$BASE_URI/$launchId"
}
