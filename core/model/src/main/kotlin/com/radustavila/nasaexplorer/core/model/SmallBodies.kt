package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import kotlin.math.pow
import kotlin.math.sqrt

/** An asteroid or comet passing close to Earth, from JPL's close-approach data (CAD). */
data class CloseApproach(
    /** Primary designation, e.g. "2026 UK" or "99942"; JPL's key for every other API. */
    val designation: String,
    /** Display name, e.g. "99942 Apophis (2004 MN4)" or "(2026 UK)". */
    val name: String,
    val time: Instant,
    /** Nominal distance from Earth's centre at closest approach, in astronomical units. */
    val distanceAu: Double,
    val relativeVelocityKmPerSecond: Double,
    /** Absolute magnitude H; brighter (smaller) means bigger. */
    val absoluteMagnitude: Double?,
    /** Measured diameter in km, which JPL rarely has for small objects. */
    val diameterKm: Double?,
    /** Present when the object is on JPL's Sentry impact-risk list. */
    val sentry: SentryRisk? = null,
) {
    val distanceLunar: Double get() = distanceAu * SmallBodyUnits.LUNAR_DISTANCES_PER_AU
    val distanceKm: Double get() = distanceAu * SmallBodyUnits.KM_PER_AU

    /** The measured diameter, or the range implied by [absoluteMagnitude]. */
    val size: SizeEstimate?
        get() = diameterKm?.let { SizeEstimate(it * M_PER_KM, it * M_PER_KM) }
            ?: absoluteMagnitude?.let(SizeEstimate::fromAbsoluteMagnitude)

    private companion object {
        const val M_PER_KM = 1_000.0
    }
}

/** A diameter in metres; a range when it is estimated from brightness alone. */
data class SizeEstimate(val minMeters: Double, val maxMeters: Double) {
    val isRange: Boolean get() = maxMeters > minMeters

    companion object {
        private const val DIAMETER_CONSTANT_KM = 1329.0
        private const val BRIGHT_ALBEDO = 0.25
        private const val DARK_ALBEDO = 0.05
        private const val M_PER_KM = 1_000.0

        /**
         * D = 1329 km / √albedo × 10^(−H/5). Asteroid albedos run from about 0.05 (dark) to 0.25
         * (bright), which spans a factor of about 2.2 in size.
         */
        fun fromAbsoluteMagnitude(h: Double): SizeEstimate {
            val base = DIAMETER_CONSTANT_KM * 10.0.pow(-h / 5) * M_PER_KM
            return SizeEstimate(minMeters = base / sqrt(BRIGHT_ALBEDO), maxMeters = base / sqrt(DARK_ALBEDO))
        }
    }
}

/** An object's entry on JPL's Sentry list of possible future Earth impacts. */
data class SentryRisk(
    val designation: String,
    /** Cumulative impact probability over all potential impacts. */
    val impactProbability: Double,
    /** Highest Palermo scale value; below −2 means no unusual concern. */
    val palermoMax: Double?,
    /** Highest Torino scale value, 0–10. */
    val torinoMax: Int?,
    val potentialImpacts: Int?,
    /** Years of the potential impacts, e.g. "2064-2118". */
    val yearRange: String?,
)

/** Orbit and physical facts about one small body, from JPL's Small-Body Database. */
data class SmallBodyDetails(
    val designation: String,
    val name: String,
    val orbitClass: String?,
    val isNearEarthObject: Boolean,
    val isPotentiallyHazardous: Boolean,
    /** Minimum orbit intersection distance with Earth, au. */
    val earthMoidAu: Double?,
    val semiMajorAxisAu: Double?,
    val eccentricity: Double?,
    val inclinationDegrees: Double?,
    val perihelionAu: Double?,
    val aphelionAu: Double?,
    val orbitalPeriodDays: Double?,
    val absoluteMagnitude: Double?,
    val diameterKm: Double?,
    val firstObserved: String?,
    val lastObserved: String?,
    val observationsUsed: Int?,
    val fetchedAt: Instant,
)

/** A bright meteor recorded by US government sensors, from JPL's fireball API. */
data class Fireball(
    val time: Instant,
    /** Total radiated energy, joules. */
    val radiatedEnergyJoules: Double,
    /** Estimated total impact energy, kilotons of TNT. */
    val impactEnergyKilotons: Double,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeKm: Double?,
    val velocityKmPerSecond: Double?,
)

object SmallBodyUnits {
    const val KM_PER_AU = 149_597_870.7

    /** Mean Earth–Moon distance is 384,400 km. */
    const val LUNAR_DISTANCES_PER_AU = KM_PER_AU / 384_400.0
}
