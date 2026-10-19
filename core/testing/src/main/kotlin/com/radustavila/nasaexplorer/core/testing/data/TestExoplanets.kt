package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.DiscoveryMethod
import com.radustavila.nasaexplorer.core.model.Exoplanet
import java.time.YearMonth

/** Builders for [Exoplanet] test data, loosely modelled on real archive rows. */
object TestExoplanets {

    fun exoplanet(
        name: String = "TOI-789 c",
        hostName: String = name.substringBeforeLast(' '),
        discoveryYear: Int = 2026,
        discoveryPublished: YearMonth? = YearMonth.of(discoveryYear, 9),
        discoveryMethod: DiscoveryMethod = DiscoveryMethod.TRANSIT,
        discoveryFacility: String? = "Transiting Exoplanet Survey Satellite (TESS)",
        radiusEarth: Double? = 1.15,
        massEarth: Double? = 3.0,
        orbitalPeriodDays: Double? = 8.0432344,
        equilibriumTemperatureKelvin: Double? = 394.0,
        distanceParsecs: Double? = 43.407,
    ) = Exoplanet(
        name = name,
        hostName = hostName,
        discoveryYear = discoveryYear,
        discoveryPublished = discoveryPublished,
        discoveryMethod = discoveryMethod,
        discoveryFacility = discoveryFacility,
        radiusEarth = radiusEarth,
        massEarth = massEarth,
        orbitalPeriodDays = orbitalPeriodDays,
        equilibriumTemperatureKelvin = equilibriumTemperatureKelvin,
        distanceParsecs = distanceParsecs,
    )

    /** A hot Jupiter found by radial velocity. */
    val hotJupiter = exoplanet(
        name = "TOI-2158 c",
        discoveryMethod = DiscoveryMethod.RADIAL_VELOCITY,
        discoveryFacility = "Multiple Facilities",
        radiusEarth = 13.5,
        massEarth = 514.88,
        orbitalPeriodDays = 414.5,
        equilibriumTemperatureKelvin = null,
        distanceParsecs = 198.473,
    )

    /** Proxima Centauri b: the nearest known exoplanet. */
    val proximaB = exoplanet(
        name = "Proxima Cen b",
        hostName = "Proxima Cen",
        discoveryYear = 2016,
        discoveryMethod = DiscoveryMethod.RADIAL_VELOCITY,
        discoveryFacility = "European Southern Observatory",
        radiusEarth = 1.03,
        massEarth = 1.07,
        orbitalPeriodDays = 11.18427,
        equilibriumTemperatureKelvin = 234.0,
        distanceParsecs = 1.30119,
    )
}
