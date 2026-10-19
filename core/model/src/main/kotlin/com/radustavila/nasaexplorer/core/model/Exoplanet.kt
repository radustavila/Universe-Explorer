package com.radustavila.nasaexplorer.core.model

import java.time.YearMonth

/**
 * A confirmed planet outside the Solar System, as the NASA Exoplanet Archive's composite
 * parameters table (`pscomppars`) describes it: one row per planet.
 *
 * Every measured quantity is nullable because the archive leaves gaps: a microlensing planet has
 * no orbital period, many have no equilibrium temperature, a few have no distance.
 *
 * @property name the archive's `pl_name`; also the planet's identity (favourites, navigation).
 * @property discoveryPublished month the discovery paper was published (`disc_pubdate`).
 * @property radiusEarth radius in Earth radii (`pl_rade`).
 * @property massEarth mass, or minimum mass, in Earth masses (`pl_bmasse`).
 * @property distanceParsecs distance of its star system from the Sun (`sy_dist`), in parsecs.
 */
data class Exoplanet(
    val name: String,
    val hostName: String,
    val discoveryYear: Int,
    val discoveryPublished: YearMonth? = null,
    val discoveryMethod: DiscoveryMethod = DiscoveryMethod.OTHER,
    val discoveryFacility: String? = null,
    val radiusEarth: Double? = null,
    val massEarth: Double? = null,
    val orbitalPeriodDays: Double? = null,
    val equilibriumTemperatureKelvin: Double? = null,
    val distanceParsecs: Double? = null,
) {
    val radiusJupiter: Double? get() = radiusEarth?.let(ExoplanetUnits::earthRadiiToJupiterRadii)
    val massJupiter: Double? get() = massEarth?.let(ExoplanetUnits::earthMassesToJupiterMasses)
    val distanceLightYears: Double? get() = distanceParsecs?.let(ExoplanetUnits::parsecsToLightYears)
}

/** How a planet was found, from the archive's `disc_method`. */
enum class DiscoveryMethod(private val archiveNames: Set<String>) {
    TRANSIT(setOf("tran", "transit")),
    RADIAL_VELOCITY(setOf("rv", "radial velocity")),
    MICROLENSING(setOf("micro", "microlensing")),
    IMAGING(setOf("ima", "imaging")),
    TRANSIT_TIMING(setOf("ttv", "transit timing variations")),
    ECLIPSE_TIMING(setOf("etv", "eclipse timing variations")),
    ORBITAL_BRIGHTNESS(setOf("obm", "orbital brightness modulation")),
    PULSAR_TIMING(setOf("pul", "pulsar timing")),
    PULSATION_TIMING(setOf("ptv", "pulsation timing variations")),
    ASTROMETRY(setOf("ast", "astrometry")),
    DISK_KINEMATICS(setOf("dkin", "disk kinematics")),
    OTHER(emptySet()),
    ;

    companion object {
        /**
         * The TAP service answers with short codes (`tran`, `rv`, …); the archive's web tables use
         * the long names. Both are accepted so a change of either does not lose the method.
         */
        fun fromArchive(value: String?): DiscoveryMethod {
            val normalized = value?.trim()?.lowercase() ?: return OTHER
            return entries.firstOrNull { normalized in it.archiveNames } ?: OTHER
        }
    }
}

/** The lists the Exoplanets screen offers over the cached catalogue. */
enum class ExoplanetList {
    /** Planets discovered this year and last, newest publication first. */
    NEWEST,

    /** Planets of the star systems closest to the Sun. */
    NEAREST,

    /** Planets between 0.8 and 1.25 Earth radii, nearest first. */
    EARTH_SIZED,
}

/** Archive-wide figures shown above the lists. */
data class ExoplanetCatalogStats(
    val confirmedPlanets: Int,
)
