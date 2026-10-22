package com.radustavila.nasaexplorer.core.model

/** A direction on the celestial sphere in the J2000 mean equator and equinox, degrees. */
data class EquatorialCoordinate(
    /** 0 to 360, eastward from the March equinox. */
    val rightAscensionDegrees: Double,
    /** -90 (south celestial pole) to 90 (north celestial pole). */
    val declinationDegrees: Double,
)

/** A direction in the observer's sky, degrees. */
data class HorizontalCoordinate(
    /** -90 (straight down) to 90 (straight up); 0 is the horizon. */
    val altitudeDegrees: Double,
    /** 0 to 360, from north through east. */
    val azimuthDegrees: Double,
)

/** One star of the bundled catalogue. */
data class Star(
    val position: EquatorialCoordinate,
    /** Apparent visual magnitude: smaller is brighter, about 6.5 is the naked-eye limit. */
    val magnitude: Float,
    /** B-V colour index, from about -0.3 (blue-white) to 2 (red); null when unknown. */
    val colourIndex: Float?,
    /** IAU proper name ("Sirius"), or null for the vast majority that have none. */
    val name: String?,
)

/** One of the 88 IAU constellations as a stick figure. Serpens comes as two figures, head and tail. */
data class ConstellationFigure(
    /** IAU three-letter abbreviation ("UMa"). */
    val id: String,
    /** IAU (Latin) name ("Ursa Major"), the same in every language. */
    val name: String,
    /** Where its name goes on a chart. */
    val labelPosition: EquatorialCoordinate,
    /** 1 for the most prominent figures, up to 3 for faint ones; decides which names show first. */
    val rank: Int,
    /** Each line joins stars in order. */
    val lines: List<List<EquatorialCoordinate>>,
)

/** The bundled star catalogue and constellation figures. */
data class SkyCatalog(
    /** Brightest first. */
    val stars: List<Star>,
    val constellations: List<ConstellationFigure>,
)

/** The Sun, the Moon and the planets drawn on the sky map. */
enum class SolarSystemBody {
    SUN,
    MOON,
    MERCURY,
    VENUS,
    MARS,
    JUPITER,
    SATURN,
    URANUS,
    NEPTUNE,
}

/** Where a Sun, Moon or planet is in the observer's sky at one instant. */
data class SolarSystemPosition(
    val body: SolarSystemBody,
    /** Geometric, without atmospheric refraction, which the map adds itself. */
    val horizontal: HorizontalCoordinate,
    /** J2000, for the constellation it is in. */
    val equatorial: EquatorialCoordinate,
    val magnitude: Double,
    /** Lit fraction of the disc, 0 to 1. */
    val illuminatedFraction: Double,
    /** Apparent diameter of the disc, degrees (about 0.5 for the Sun and the Moon). */
    val angularDiameterDegrees: Double,
)

/** Where a satellite is in the observer's sky at one instant. */
data class SatelliteSkyPosition(
    val noradId: Int,
    val name: String,
    /** Geometric, without atmospheric refraction. */
    val horizontal: HorizontalCoordinate,
)

/** Everything on the sky map that moves against the stars, for one place and instant. */
data class SkyMapBodies(
    val solarSystem: List<SolarSystemPosition>,
    /** Empty without orbital elements, or when they are too old to trust. */
    val satellites: List<SatelliteSkyPosition>,
)
