package com.radustavila.nasaexplorer.core.model

/**
 * Unit conversions for the Exoplanet Archive's quantities, which come in parsecs, Earth radii and
 * Earth masses. Constants are the IAU nominal values the archive itself uses.
 */
object ExoplanetUnits {

    /** One parsec in light-years. */
    const val LIGHT_YEARS_PER_PARSEC = 3.261_563_777

    /** Jupiter's equatorial radius in Earth radii (71,492 km / 6,378.1 km). */
    const val EARTH_RADII_PER_JUPITER_RADIUS = 11.209

    /** Jupiter's mass in Earth masses. */
    const val EARTH_MASSES_PER_JUPITER_MASS = 317.83

    /** Radius range (in Earth radii) the "Earth-sized" list uses. */
    val EARTH_SIZED_RADII: ClosedFloatingPointRange<Double> = 0.8..1.25

    fun parsecsToLightYears(parsecs: Double): Double = parsecs * LIGHT_YEARS_PER_PARSEC

    fun earthRadiiToJupiterRadii(earthRadii: Double): Double = earthRadii / EARTH_RADII_PER_JUPITER_RADIUS

    fun earthMassesToJupiterMasses(earthMasses: Double): Double = earthMasses / EARTH_MASSES_PER_JUPITER_MASS

    /** Kelvin to degrees Celsius. */
    fun kelvinToCelsius(kelvin: Double): Double = kelvin - ZERO_CELSIUS_IN_KELVIN

    private const val ZERO_CELSIUS_IN_KELVIN = 273.15
}
