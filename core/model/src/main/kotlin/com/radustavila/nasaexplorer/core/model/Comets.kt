package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * A comet near perihelion that could become bright enough to look for (F18), from JPL's Small-Body
 * Database, with Horizons' daily ephemeris around today.
 */
data class Comet(
    /** JPL's primary designation, the key: "2026 A2", "12P". */
    val designation: String,
    /** "C/2026 A2 (Bok)". */
    val name: String,
    /** Total absolute magnitude M1: how bright it would be 1 au from both the Sun and Earth. */
    val absoluteMagnitude: Double?,
    /** Brightness slope K1 (2.5 n): how fast it brightens as it nears the Sun. */
    val slopeParameter: Double?,
    val perihelionDistanceAu: Double,
    val eccentricity: Double,
    val perihelionTime: Instant?,
    /** One point a day, oldest first; empty until Horizons answered for this comet. */
    val ephemeris: List<CometEphemerisPoint>,
)

/** Where a comet is seen from Earth's centre at [time], and how bright it is predicted to be. */
data class CometEphemerisPoint(
    val time: Instant,
    /** Astrometric J2000 (ICRF) position. */
    val position: EquatorialCoordinate,
    /** Predicted total visual magnitude; null where JPL has no brightness model. */
    val magnitude: Double?,
    val sunDistanceAu: Double,
    val earthDistanceAu: Double,
)

/**
 * The everyday name in a comet's full JPL name: "Bok" for "C/2026 A2 (Bok)", "Pons-Brooks" for
 * "12P/Pons-Brooks", or the whole name when it has none ("P/2019 LD2").
 */
fun cometShortName(fullName: String): String {
    val inParentheses = fullName.substringAfterLast('(', "").substringBefore(')').trim()
    val afterSlash = fullName.substringAfter('/', "").trim()
    return when {
        inParentheses.isNotEmpty() -> inParentheses
        afterSlash.isNotEmpty() && afterSlash.first().isLetter() -> afterSlash
        else -> fullName
    }
}

/** What it takes to see a comet of a given predicted magnitude under a dark sky. */
enum class CometVisibility {
    NAKED_EYE,
    BINOCULARS,
    TELESCOPE,

    /** Beyond small amateur telescopes: listed, but not "visible now". */
    TOO_FAINT,
    ;

    companion object {
        /**
         * Comets are diffuse, so these limits are a magnitude or more brighter than a star's would
         * be. Predictions are often off by a magnitude, which the screens say.
         */
        fun fromMagnitude(magnitude: Double?): CometVisibility = when {
            magnitude == null -> TOO_FAINT
            magnitude <= NAKED_EYE_LIMIT -> NAKED_EYE
            magnitude <= BINOCULARS_LIMIT -> BINOCULARS
            magnitude <= TELESCOPE_LIMIT -> TELESCOPE
            else -> TOO_FAINT
        }

        const val NAKED_EYE_LIMIT = 6.0
        const val BINOCULARS_LIMIT = 9.0

        /** The cut-off of "visible now": what a 15-20 cm telescope shows under a dark sky. */
        const val TELESCOPE_LIMIT = 12.0
    }
}

/**
 * One comet tonight, for the saved place when there is one: its predicted brightness now, where it
 * is, and when it stands highest in a dark sky. Computed on the device from the cached ephemeris.
 */
data class CometTonight(
    val comet: Comet,
    val computedAt: Instant,
    /** Predicted total magnitude now, rounded by the screens. */
    val magnitude: Double?,
    val visibility: CometVisibility,
    /** J2000 position now. */
    val position: EquatorialCoordinate,
    /** Constellation name, e.g. "Ursa Major". */
    val constellation: String,
    val sunDistanceAu: Double,
    val earthDistanceAu: Double,
    /** The brightest point of the cached ephemeris (±60 days around the last refresh). */
    val peakMagnitude: Double?,
    val peakTime: Instant?,
    /** Null without a saved place. */
    val sky: CometLocalSky?,
)

/** A comet's path across one observer's sky over the next day. */
data class CometLocalSky(
    /** Where it is right now. */
    val now: HorizontalCoordinate,
    /** First rise within a day; null when it neither rises nor sets in that time. */
    val rise: Instant?,
    /** First set within a day. */
    val set: Instant?,
    /** Above the horizon the whole day (circumpolar for now). */
    val isAlwaysUp: Boolean,
    /**
     * When it stands highest while the Sun is at least 12 degrees down, tonight or the coming night;
     * null when it stays below [MIN_VISIBLE_ALTITUDE] all that time.
     */
    val bestTime: Instant?,
    val best: HorizontalCoordinate?,
) {
    val isVisibleTonight: Boolean get() = bestTime != null

    companion object {
        /** Faint, diffuse light needs to clear the haze near the horizon. */
        const val MIN_VISIBLE_ALTITUDE = 15.0
    }
}

/** Every cached comet tonight, split by whether it is bright enough to look for. */
data class CometsTonight(
    val observer: GeoCoordinate?,
    /** At or brighter than [CometVisibility.TELESCOPE_LIMIT], brightest first. */
    val visible: List<CometTonight>,
    /** The other candidates, brightest first. */
    val faint: List<CometTonight>,
)
