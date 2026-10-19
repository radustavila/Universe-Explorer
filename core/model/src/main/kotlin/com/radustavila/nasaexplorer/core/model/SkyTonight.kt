package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * Tonight's sky above one place, computed on the device: when it gets dark, the Moon, and which
 * planets can be seen.
 */
data class SkyTonight(
    val computedAt: Instant,
    val night: NightWindow,
    val moon: MoonTonight,
    /** Every naked-eye planet, visible ones first, each with its best time tonight. */
    val planets: List<PlanetTonight>,
)

/**
 * The night that is under way, or the next one: from this evening's sunset to the following
 * sunrise, with the twilight stages in between.
 *
 * Any instant is null where the event does not happen that night, e.g. at high latitudes in summer,
 * when the Sun never gets 18 degrees below the horizon.
 */
data class NightWindow(
    val sunset: Instant?,
    /** Sun 6 degrees below the horizon: the brightest planets show up. */
    val civilDusk: Instant?,
    /** Sun 12 degrees below the horizon: dark enough for most stars. */
    val nauticalDusk: Instant?,
    /** Sun 18 degrees below the horizon: fully dark. */
    val astronomicalDusk: Instant?,
    val astronomicalDawn: Instant?,
    val nauticalDawn: Instant?,
    val civilDawn: Instant?,
    val sunrise: Instant?,
)

/** The eight traditional phases, from the Moon's ecliptic longitude relative to the Sun. */
enum class MoonPhase {
    NEW_MOON,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL_MOON,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT,
    ;

    companion object {
        /**
         * @param phaseAngle degrees from 0 (new) through 90 (first quarter), 180 (full) and 270
         * (last quarter). The four principal phases are instants; each is given a 1/16 of the
         * cycle on either side, about a day, as almanacs do.
         */
        fun fromPhaseAngle(phaseAngle: Double): MoonPhase {
            val normalized = ((phaseAngle % FULL_TURN) + FULL_TURN) % FULL_TURN
            val index = ((normalized + HALF_SECTOR) / SECTOR).toInt() % entries.size
            return entries[index]
        }

        private const val FULL_TURN = 360.0
        private const val SECTOR = FULL_TURN / 8
        private const val HALF_SECTOR = SECTOR / 2
    }
}

data class MoonTonight(
    val phase: MoonPhase,
    /** 0 = new, 90 = first quarter, 180 = full, 270 = last quarter. */
    val phaseAngleDegrees: Double,
    /** 0.0 to 1.0. */
    val illuminatedFraction: Double,
    /** First moonrise within a day after tonight's sunset; null when there is none. */
    val rise: Instant?,
    /** First moonset within a day after tonight's sunset. */
    val set: Instant?,
    /** Already above the horizon at sunset, so [rise] is tomorrow's. */
    val isUpAtStart: Boolean,
    val nextFullMoon: Instant,
    val nextNewMoon: Instant,
)

/** The planets bright enough to find without a telescope. */
enum class Planet {
    MERCURY,
    VENUS,
    MARS,
    JUPITER,
    SATURN,
}

data class PlanetTonight(
    val planet: Planet,
    /** First rise within a day after tonight's sunset; null when there is none. */
    val rise: Instant?,
    /** First set within a day after tonight's sunset. */
    val set: Instant?,
    /** Already above the horizon at sunset, so [rise] is tomorrow's. */
    val isUpAtStart: Boolean,
    /**
     * When the planet stands highest while the sky is dark enough to see it (between civil dusk
     * and civil dawn, Sun 6 degrees down), or null when it stays below [MIN_VISIBLE_ALTITUDE] the
     * whole time.
     */
    val bestTime: Instant?,
    /** Altitude above the horizon at [bestTime], degrees. */
    val bestAltitudeDegrees: Double?,
    /** Compass direction at [bestTime], degrees from north through east. */
    val bestAzimuthDegrees: Double?,
    /** Apparent visual magnitude tonight: lower is brighter (Venus about -4, Saturn about +1). */
    val magnitude: Double,
    /** Constellation the planet is in, e.g. "Sagittarius". */
    val constellation: String,
) {
    val isVisibleTonight: Boolean get() = bestTime != null

    companion object {
        /** Below this, haze, buildings and trees hide most planets. */
        const val MIN_VISIBLE_ALTITUDE = 10.0
    }
}
