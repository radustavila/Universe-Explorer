package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * Mean orbital elements of one satellite (CelesTrak GP data, the OMM form of a two-line element
 * set), the input SGP4 needs to predict where the satellite will be.
 *
 * They age quickly for a low orbit: predictions are good to about a minute for a few days after
 * [epoch] and drift after that, so they are refreshed daily.
 */
data class OrbitalElements(
    val noradId: Int,
    val name: String,
    val epoch: Instant,
    /** Revolutions per day. */
    val meanMotion: Double,
    val eccentricity: Double,
    val inclinationDegrees: Double,
    /** Right ascension of the ascending node. */
    val raanDegrees: Double,
    val argOfPericenterDegrees: Double,
    val meanAnomalyDegrees: Double,
    /** SGP4 drag term, in inverse Earth radii. */
    val bstar: Double,
) {
    companion object {
        /** The International Space Station's catalogue number. */
        const val ISS_NORAD_ID = 25544
    }
}

/** One pass of a satellite over the observer, from when it climbs above the minimum elevation. */
data class SatellitePass(
    val start: Instant,
    /** Highest point of the pass. */
    val peak: Instant,
    val end: Instant,
    val maxElevationDegrees: Double,
    /** Compass directions, degrees from north through east. */
    val startAzimuthDegrees: Double,
    val endAzimuthDegrees: Double,
    /**
     * Whether it can be seen with the naked eye: the satellite is in sunlight while the observer's
     * sky is dark (Sun at least 6 degrees below the horizon) at some point of the pass.
     */
    val isVisible: Boolean,
)

/** Where a satellite is above the Earth at [at]. */
data class SatellitePosition(
    val coordinate: GeoCoordinate,
    val altitudeKm: Double,
    val speedKmPerHour: Double,
    /** Whether the satellite is in sunlight (null when unknown). */
    val isSunlit: Boolean?,
    val at: Instant,
    /** True when it comes from a live tracking service, false when propagated from cached elements. */
    val isLive: Boolean,
)

/** Someone in orbit right now, as listed by Launch Library 2. */
data class PersonInSpace(
    val id: Int,
    val name: String,
    /** e.g. "NASA", "RFSA", "CNSA"; the abbreviation when the API has one. */
    val agency: String?,
    val nationality: String?,
    val imageUrl: String?,
    /** Launch of their current flight, i.e. how long they have been up. */
    val launchedAt: Instant?,
)
