package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.ConstellationFigure
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.HorizontalCoordinate
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.model.SolarSystemPosition
import com.radustavila.nasaexplorer.core.model.Star

/** A small sky: Orion's brightest stars and figure, Sirius and Polaris, and a few bodies. */
@Suppress("MagicNumber")
object TestSkyMap {

    fun star(
        rightAscension: Double,
        declination: Double,
        magnitude: Float,
        name: String? = null,
        colourIndex: Float? = 0.5f,
    ) = Star(EquatorialCoordinate(rightAscension, declination), magnitude, colourIndex, name)

    val SIRIUS = star(101.2872, -16.7161, -1.44f, "Sirius", 0.009f)
    val BETELGEUSE = star(88.7929, 7.4071, 0.45f, "Betelgeuse", 1.5f)
    val RIGEL = star(78.6345, -8.2016, 0.18f, "Rigel", -0.03f)
    val BELLATRIX = star(81.2828, 6.3497, 1.64f, "Bellatrix", -0.22f)
    val SAIPH = star(86.9391, -9.6696, 2.07f, "Saiph", -0.17f)
    val POLARIS = star(37.9546, 89.2641, 1.97f, "Polaris", 0.64f)

    fun orion() = ConstellationFigure(
        id = "Ori",
        name = "Orion",
        labelPosition = EquatorialCoordinate(83.0, 4.0),
        rank = 1,
        lines = listOf(
            listOf(BETELGEUSE.position, BELLATRIX.position, RIGEL.position, SAIPH.position, BETELGEUSE.position),
        ),
    )

    fun catalog() = SkyCatalog(
        stars = listOf(SIRIUS, RIGEL, BETELGEUSE, BELLATRIX, POLARIS, SAIPH).sortedBy { it.magnitude },
        constellations = listOf(orion()),
    )

    fun position(
        body: SolarSystemBody,
        altitude: Double,
        azimuth: Double,
        magnitude: Double = 0.0,
        illuminatedFraction: Double = 1.0,
        angularDiameter: Double = 0.01,
    ) = SolarSystemPosition(
        body = body,
        horizontal = HorizontalCoordinate(altitude, azimuth),
        equatorial = EquatorialCoordinate(0.0, 0.0),
        magnitude = magnitude,
        illuminatedFraction = illuminatedFraction,
        angularDiameterDegrees = angularDiameter,
    )

    fun solarSystem() = listOf(
        position(SolarSystemBody.SUN, altitude = -30.0, azimuth = 0.0, magnitude = -26.7, angularDiameter = 0.53),
        position(
            SolarSystemBody.MOON,
            altitude = 35.0,
            azimuth = 120.0,
            magnitude = -11.0,
            illuminatedFraction = 0.6,
            angularDiameter = 0.52,
        ),
        position(SolarSystemBody.JUPITER, altitude = 50.0, azimuth = 160.0, magnitude = -2.5),
        position(SolarSystemBody.SATURN, altitude = -10.0, azimuth = 270.0, magnitude = 0.8),
    )
}
