package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay

/**
 * What kind of deep-sky object (F23). The bundled catalogue stores the [ordinal]: keep the order in
 * step with `core/skymap/tools/build_sky_catalog.py`. The observing log export and the quiz name
 * it, so never rename an entry.
 */
enum class DeepSkyType {
    GALAXY,
    OPEN_CLUSTER,
    GLOBULAR_CLUSTER,
    PLANETARY_NEBULA,

    /** Emission, reflection and star-forming nebulae, with or without their cluster. */
    NEBULA,
    SUPERNOVA_REMNANT,
    DARK_NEBULA,

    /** Star clouds, asterisms and the double star M40. */
    STAR_GROUP,
}

/** The two checklists of the observing log. */
enum class DeepSkyCatalogue(val size: Int) {
    MESSIER(size = 110),
    CALDWELL(size = 109),
}

/** The least a deep-sky object needs to be seen, from its brightness. */
enum class ObservingInstrument {
    NAKED_EYE,
    BINOCULARS,
    SMALL_TELESCOPE,
    TELESCOPE,
}

/**
 * A galaxy, cluster or nebula of the bundled catalogue (OpenNGC, CC BY-SA 4.0): every object below
 * magnitude 12 and all Messier and Caldwell objects.
 */
data class DeepSkyObject(
    /** OpenNGC's name ("NGC0224", "Mel022"): the observing log's key, so it never changes. */
    val id: String,
    /** "NGC 224"; empty for the few known only by their Messier or Caldwell number. */
    val designation: String,
    /** The first of OpenNGC's common names ("Andromeda Galaxy"), in English as the catalogue gives it. */
    val commonName: String?,
    val type: DeepSkyType,
    val position: EquatorialCoordinate,
    /** IAU abbreviation ("And"). */
    val constellation: String,
    /** Visual magnitude, or blue where the catalogue has no visual one; null when unknown. */
    val magnitude: Float?,
    val majorAxisArcmin: Float? = null,
    val minorAxisArcmin: Float? = null,
    /** Of the major axis, from north through east. */
    val positionAngleDegrees: Int? = null,
    val messier: Int? = null,
    val caldwell: Int? = null,
) {
    /** NASA's Hubble Messier Catalog page about it, for "read more"; null outside Messier's list. */
    val nasaPageUrl: String?
        get() = messier?.let { "$HUBBLE_MESSIER_CATALOG/messier-$it/" }

    fun numberIn(catalogue: DeepSkyCatalogue): Int? = when (catalogue) {
        DeepSkyCatalogue.MESSIER -> messier
        DeepSkyCatalogue.CALDWELL -> caldwell
    }

    /**
     * The month it is highest in the sky at about 10 pm local time, when most people observe: the
     * Sun is then about 150 degrees of right ascension behind it.
     */
    val bestMonth: Month
        get() {
            val sunLongitude = (position.rightAscensionDegrees - SUN_BEHIND_AT_TEN_PM).mod(FULL_TURN)
            val days = (sunLongitude / DEGREES_PER_DAY).toLong()
            return MARCH_EQUINOX.atYear(NON_LEAP_YEAR).plusDays(days).month
        }

    val instrument: ObservingInstrument
        get() {
            val major = majorAxisArcmin ?: 0f
            val magnitude = magnitude ?: return if (type == DeepSkyType.DARK_NEBULA && major >= LARGE_DARK_NEBULA) {
                ObservingInstrument.NAKED_EYE
            } else {
                ObservingInstrument.TELESCOPE
            }
            // Galaxies spread their light thin: they need more aperture than a cluster as bright.
            val penalty = if (type == DeepSkyType.GALAXY) GALAXY_PENALTY else 0f
            return when {
                magnitude + penalty <= NAKED_EYE_LIMIT -> ObservingInstrument.NAKED_EYE
                magnitude + penalty <= BINOCULAR_LIMIT -> ObservingInstrument.BINOCULARS
                magnitude + penalty <= SMALL_TELESCOPE_LIMIT -> ObservingInstrument.SMALL_TELESCOPE
                else -> ObservingInstrument.TELESCOPE
            }
        }

    private companion object {
        const val HUBBLE_MESSIER_CATALOG =
            "https://science.nasa.gov/mission/hubble/science/explore-the-night-sky/hubble-messier-catalog"
        const val FULL_TURN = 360.0
        const val SUN_BEHIND_AT_TEN_PM = 150.0
        const val DEGREES_PER_DAY = 360.0 / 365.25
        val MARCH_EQUINOX: MonthDay = MonthDay.of(Month.MARCH, 20)
        const val NON_LEAP_YEAR = 2025
        const val NAKED_EYE_LIMIT = 4.5f
        const val BINOCULAR_LIMIT = 8.5f
        const val SMALL_TELESCOPE_LIMIT = 10.5f
        const val GALAXY_PENALTY = 0.5f
        const val LARGE_DARK_NEBULA = 60f
    }
}

/** How the sky was when an object was seen. Stored by [name]: never rename an entry. */
enum class ObservingConditions {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    ;

    companion object {
        fun fromName(name: String?): ObservingConditions? = entries.firstOrNull { it.name == name }
    }
}

/** A "seen" mark in the observing log: one per object, kept in the favourites database. */
data class ObservingLogEntry(
    val objectId: String,
    val observedOn: LocalDate,
    val conditions: ObservingConditions? = null,
    val note: String? = null,
    /** When it was marked or last edited. */
    val loggedAt: Instant,
)

/** Where a deep-sky object is best seen during tonight's dark hours. */
data class DeepSkyVisibility(
    val objectId: String,
    /** When it is highest during the dark hours. */
    val bestTime: Instant,
    val altitudeDegrees: Double,
    val azimuthDegrees: Double,
)

/** Tonight's dark hours at the user's place and what climbs high enough in them. */
data class DeepSkyTonight(
    val darkStart: Instant,
    val darkEnd: Instant,
    /** By object id: those at least [MIN_ALTITUDE_DEGREES] up at some point of the dark hours. */
    val visible: Map<String, DeepSkyVisibility>,
) {
    companion object {
        /** Lower down, the haze near the horizon washes faint objects out. */
        const val MIN_ALTITUDE_DEGREES = 30.0
    }
}
