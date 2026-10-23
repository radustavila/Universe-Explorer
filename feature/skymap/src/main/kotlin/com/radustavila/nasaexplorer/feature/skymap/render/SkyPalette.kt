package com.radustavila.nasaexplorer.feature.skymap.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.core.skymap.StarStyle

/** Colours of the map. [NIGHT] keeps to dim reds, which leave eyes adapted to the dark. */
@Immutable
internal data class SkyPalette(
    val night: Color,
    /** Sky colour with the Sun well up; the background blends towards it through twilight. */
    val day: Color,
    val ground: Color,
    val horizon: Color,
    val cardinal: Color,
    val north: Color,
    val constellationLine: Color,
    val highlightedLine: Color,
    val constellationLabel: Color,
    val starLabel: Color,
    val bodyLabel: Color,
    val selection: Color,
    val iss: Color,
    val comet: Color,
    val moonDark: Color,
    val moonLit: Color,
    val sun: Color,
    /** Per [StarStyle] colour bucket; null to use the stars' own colours. */
    val starColour: Color?,
    private val planetColours: Map<SolarSystemBody, Color>,
) {
    fun planet(body: SolarSystemBody): Color = planetColours[body] ?: bodyLabel

    fun star(bucket: Int): Color = starColour ?: Color(StarStyle.colour(bucket))

    /** The background for a Sun at [sunAltitude] degrees: black once it is 12 degrees down. */
    fun background(sunAltitude: Double?): Color {
        if (sunAltitude == null) return night
        val daylight = ((sunAltitude + NAUTICAL_TWILIGHT) / (NAUTICAL_TWILIGHT + FULL_DAY)).coerceIn(0.0, 1.0)
        return lerp(night, day, daylight.toFloat())
    }

    companion object {
        private const val NAUTICAL_TWILIGHT = 12.0
        private const val FULL_DAY = 6.0

        val REGULAR = SkyPalette(
            night = Color(0xFF04060E),
            day = Color(0xFF1E4C8C),
            ground = Color(0xD9101A12),
            horizon = Color(0xFF5E9C6E),
            cardinal = Color(0xFFB9D4BF),
            north = Color(0xFFFF8A80),
            constellationLine = Color(0x664F7BD6),
            highlightedLine = Color(0xCC8FB4FF),
            constellationLabel = Color(0xAA8FA8E0),
            starLabel = Color(0xCCDCE3F5),
            bodyLabel = Color(0xFFFFE082),
            selection = Color(0xFF57D0E8),
            iss = Color(0xFFFFFFFF),
            comet = Color(0xFFA5F2D8),
            moonDark = Color(0xFF2B2B33),
            moonLit = Color(0xFFECECF3),
            sun = Color(0xFFFFD54F),
            starColour = null,
            planetColours = mapOf(
                SolarSystemBody.MERCURY to Color(0xFFD7CCC8),
                SolarSystemBody.VENUS to Color(0xFFFFF8E1),
                SolarSystemBody.MARS to Color(0xFFFF8A65),
                SolarSystemBody.JUPITER to Color(0xFFFFE0B2),
                SolarSystemBody.SATURN to Color(0xFFFFECB3),
                SolarSystemBody.URANUS to Color(0xFFB2EBF2),
                SolarSystemBody.NEPTUNE to Color(0xFF90CAF9),
            ),
        )

        private val RED = Color(0xFFE53935)
        private val DIM_RED = Color(0xFF9A1B1B)

        val NIGHT = SkyPalette(
            night = Color.Black,
            day = Color.Black,
            ground = Color(0xE6140000),
            horizon = DIM_RED,
            cardinal = DIM_RED,
            north = RED,
            constellationLine = Color(0x77701010),
            highlightedLine = Color(0xCCB71C1C),
            constellationLabel = Color(0xAA8E1A1A),
            starLabel = DIM_RED,
            bodyLabel = RED,
            selection = RED,
            iss = RED,
            comet = RED,
            moonDark = Color(0xFF1E0505),
            moonLit = Color(0xFFC62828),
            sun = RED,
            starColour = RED,
            planetColours = emptyMap(),
        )
    }
}
