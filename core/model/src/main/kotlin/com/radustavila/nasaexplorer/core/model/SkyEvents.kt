package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The groups the sky events calendar can be filtered by. */
enum class SkyEventCategory {
    ECLIPSES,
    MOON,
    PLANETS,
    METEORS,

    /** The perihelia of the comets F18 tracks that are predicted to be bright enough to look for. */
    COMETS,
    SEASONS,
}

/**
 * Something that happens in the sky at a known moment, computed on the device for any date: an
 * eclipse, a Moon phase, a planet at its best, a meteor shower's peak, an equinox or solstice.
 *
 * Events are worked out for an optional observer. Without one, only the global facts are filled
 * in (when it happens, how big it is) and every local part (`local`, `viewing`) is null.
 */
sealed interface SkyEvent {

    /**
     * Stable across recomputations and observers, so a reminder can find the event again: a kind
     * and the UTC date of [time], e.g. `lunar-eclipse:2026-03-03` or `meteors-perseids:2026-08-12`.
     * See [SkyEventId].
     */
    val id: String

    /** The moment the event is listed at: its peak, or the exact instant of a phase or season. */
    val time: Instant

    /**
     * When it is first worth watching from the observer's place, which reminders count their
     * lead time back from: the start of a visible eclipse or transit, the evening a meteor
     * shower's peak night gets dark. [time] when there is nothing earlier to see.
     */
    val start: Instant get() = time

    val category: SkyEventCategory
}

/** How the Earth's shadow covers the Moon. */
enum class LunarEclipseType {
    /** Only the faint outer shadow: a slight dimming that is easy to miss. */
    PENUMBRAL,

    /** Part of the Moon goes dark in the umbra. */
    PARTIAL,

    /** The whole Moon is in the umbra and turns red. */
    TOTAL,
}

data class LunarEclipse(
    override val time: Instant,
    val type: LunarEclipseType,
    /** Fraction of the Moon's disc inside the umbra at [time]: 0 for penumbral, 1 for total. */
    val obscuration: Double,
    val penumbralStart: Instant,
    val penumbralEnd: Instant,
    /** Null for a penumbral eclipse. */
    val partialStart: Instant?,
    val partialEnd: Instant?,
    /** Null unless total. */
    val totalStart: Instant?,
    val totalEnd: Instant?,
    /** Null without an observer. */
    val local: LocalLunarEclipse?,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("lunar-eclipse", time)
    override val category: SkyEventCategory get() = SkyEventCategory.ECLIPSES

    /** The umbral phase (the part worth watching), or the penumbral one when there is no umbral. */
    val mainPhaseStart: Instant get() = partialStart ?: penumbralStart
    val mainPhaseEnd: Instant get() = partialEnd ?: penumbralEnd

    override val start: Instant get() = if (local?.isVisible == true) mainPhaseStart else time
}

/** A lunar eclipse as seen from the observer: the Moon has to be above the horizon. */
data class LocalLunarEclipse(
    /** The Moon's altitude at the eclipse's peak, degrees; negative when it is below the horizon. */
    val moonAltitudeAtPeak: Double,
    /** The Moon is above the horizon for at least part of the main phase. */
    val isVisible: Boolean,
)

/** How the Moon covers the Sun, from the place where the eclipse is greatest or from the observer. */
enum class SolarEclipseType {
    PARTIAL,

    /** The Moon is too far to cover the whole Sun: a ring of sunlight stays around it. */
    ANNULAR,
    TOTAL,
}

data class SolarEclipse(
    /** Peak of the eclipse anywhere on Earth. */
    override val time: Instant,
    /** The eclipse's type where it is greatest. */
    val type: SolarEclipseType,
    /**
     * Where on Earth the Moon's shadow axis passes closest to the Earth's centre, at [time]; null
     * for a partial eclipse, whose shadow axis misses the Earth.
     */
    val greatestEclipseAt: GeoCoordinate?,
    /** Null without an observer, or when no part of the eclipse can be seen from the observer's place. */
    val local: LocalSolarEclipse?,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("solar-eclipse", time)
    override val category: SkyEventCategory get() = SkyEventCategory.ECLIPSES
    override val start: Instant get() = local?.partialStart ?: time
}

/** A solar eclipse as seen from the observer's place, with the Sun's altitude at each contact. */
data class LocalSolarEclipse(
    /** Total or annular only inside the narrow path of the Moon's shadow; partial elsewhere. */
    val type: SolarEclipseType,
    /** Fraction of the Sun's disc the Moon covers at [peak], 0 to 1. */
    val obscuration: Double,
    val partialStart: Instant,
    /** Null unless total or annular here. */
    val totalStart: Instant?,
    val peak: Instant,
    val totalEnd: Instant?,
    val partialEnd: Instant,
    /** The Sun's altitude at [peak], degrees. Low means a clear view to the horizon is needed. */
    val sunAltitudeAtPeak: Double,
)

/** The four instants of the Moon's cycle. */
enum class MoonQuarter {
    NEW_MOON,
    FIRST_QUARTER,
    FULL_MOON,
    LAST_QUARTER,
}

/**
 * A full Moon noticeably bigger or smaller than usual, by the common definition (Richard Nolle's):
 * within 10 % of that orbit's perigee distance, or of its apogee distance.
 */
enum class MoonSize {
    SUPERMOON,
    MICROMOON,
}

data class MoonPhaseEvent(
    override val time: Instant,
    val quarter: MoonQuarter,
    /** Centre-to-centre distance from the Earth at [time]. */
    val distanceKm: Double,
    /** Only full Moons get one: a new Moon cannot be seen. Null for an ordinary full Moon. */
    val size: MoonSize? = null,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("moon-${quarter.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.MOON
}

/** An outer planet opposite the Sun: up all night, at its closest and brightest of the year. */
data class PlanetOpposition(
    override val time: Instant,
    val planet: Planet,
    /** Apparent visual magnitude at [time]: lower is brighter. */
    val magnitude: Double,
    /** Distance from the Earth at [time], astronomical units. */
    val distanceAu: Double,
    /** Constellation the planet is in, e.g. "Leo". */
    val constellation: String,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("opposition-${planet.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.PLANETS
}

/** Whether an inner planet shows before sunrise or after sunset. */
enum class Apparition {
    MORNING,
    EVENING,
}

/** Mercury or Venus at its greatest angle from the Sun: the best time to find it. */
data class GreatestElongation(
    override val time: Instant,
    val planet: Planet,
    val apparition: Apparition,
    /** Angle from the Sun, degrees. */
    val elongationDegrees: Double,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("elongation-${planet.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.PLANETS
}

/** Mercury or Venus crossing the face of the Sun (rare: Mercury's next is in 2032). */
data class PlanetTransit(
    /** Middle of the transit. */
    override val time: Instant,
    val planet: Planet,
    val transitStart: Instant,
    val transitEnd: Instant,
    /** Whether the Sun is up for part of it at the observer's place; null without an observer. */
    val isVisibleLocally: Boolean?,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("transit-${planet.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.PLANETS
    override val start: Instant get() = if (isVisibleLocally == true) transitStart else time
}

/** The four turning points of the year, named after their month (the seasons flip between hemispheres). */
enum class SeasonMarker {
    MARCH_EQUINOX,
    JUNE_SOLSTICE,
    SEPTEMBER_EQUINOX,
    DECEMBER_SOLSTICE,
}

data class SeasonEvent(
    override val time: Instant,
    val marker: SeasonMarker,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("season-${marker.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.SEASONS
}

/** The yearly meteor showers the calendar lists; their data is in `:core:astro`'s table. */
enum class MeteorShower {
    QUADRANTIDS,
    LYRIDS,
    ETA_AQUARIIDS,
    SOUTHERN_DELTA_AQUARIIDS,
    PERSEIDS,
    DRACONIDS,
    ORIONIDS,
    LEONIDS,
    GEMINIDS,
    URSIDS,
}

data class MeteorShowerPeak(
    /** The expected maximum. Showers stay strong for a few hours to a day around it. */
    override val time: Instant,
    val shower: MeteorShower,
    /** First and last day of activity this year (UTC dates). */
    val activeFrom: LocalDate,
    val activeUntil: LocalDate,
    /**
     * Zenithal hourly rate: meteors an hour under a perfectly dark sky with the radiant overhead.
     * Real counts are lower, more so with moonlight or a low radiant.
     */
    val zenithalHourlyRate: Int,
    /** The comet or asteroid whose debris the Earth passes through, e.g. "109P/Swift–Tuttle". */
    val parentBody: String,
    /** Constellation the meteors seem to come from, e.g. "Perseus". */
    val radiantConstellation: String,
    /** How much of the Moon is lit at [time], 0 to 1: a bright Moon drowns all but the brightest meteors. */
    val moonIlluminatedFraction: Double,
    /** The best night around the peak from the observer's place; null without an observer or a dark night. */
    val viewing: MeteorViewing?,
) : SkyEvent {
    override val id: String get() = SkyEventId.of("meteors-${shower.slug()}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.METEORS
    override val start: Instant get() = viewing?.darkFrom ?: time
}

/** When to watch a meteor shower from the observer's place, and where its radiant is then. */
data class MeteorViewing(
    /** Nautical dusk (Sun 12 degrees down) of the darkness closest to the peak. */
    val darkFrom: Instant,
    /** Nautical dawn ending that darkness. */
    val darkUntil: Instant,
    /** When the radiant stands highest while it is dark: most meteors are seen then. */
    val bestTime: Instant,
    /** Radiant altitude at [bestTime], degrees; below 0 the shower is hardly seen from here. */
    val radiantAltitudeDegrees: Double,
    /** Radiant compass direction at [bestTime], degrees from north through east. */
    val radiantAzimuthDegrees: Double,
)

/**
 * A comet F18 tracks at its closest to the Sun, around when it is usually brightest. Not computed
 * like the other events: it comes from JPL's orbit in the comets cache, and only for comets
 * predicted to become bright enough to look for.
 */
data class CometPerihelion(
    override val time: Instant,
    /** JPL's designation ("2026 A2"), which opens the comet's details. */
    val designation: String,
    /** "C/2026 A2 (Bok)". */
    val name: String,
    val perihelionDistanceAu: Double,
    /** Predicted total magnitude at [time]; null when the cached ephemeris does not reach it. */
    val magnitude: Double?,
) : SkyEvent {
    override val id: String
        get() = SkyEventId.of("comet-perihelion-${designation.lowercase().replace(NOT_ID_CHARACTERS, "-")}", time)
    override val category: SkyEventCategory get() = SkyEventCategory.COMETS

    private companion object {
        val NOT_ID_CHARACTERS = Regex("[^a-z0-9]+")
    }
}

/**
 * Builds and reads [SkyEvent.id]s: `<kind>:<UTC date of the event's time>`. The date lets a
 * reminder find its event again by recomputing only a few days around it.
 */
object SkyEventId {
    private const val SEPARATOR = ':'

    fun of(kind: String, time: Instant): String = "$kind$SEPARATOR${time.atOffset(ZoneOffset.UTC).toLocalDate()}"

    /** The UTC date in [id], or null when it is not a sky event id. */
    fun dateOf(id: String): LocalDate? = runCatching {
        LocalDate.parse(id.substringAfterLast(SEPARATOR, missingDelimiterValue = ""))
    }.getOrNull()
}

/** Lowercase, dash-separated enum name for ids: `FULL_MOON` is `full-moon`. */
private fun Enum<*>.slug(): String = name.lowercase().replace('_', '-')

/**
 * In-app address of the sky events calendar, shared by the navigation graph (which resolves it) and
 * the reminder notification (which opens one event), like [SpaceWeatherDeepLink].
 */
object SkyEventsDeepLink {
    const val URI = "nasaexplorer://skyevents"

    /** Opens the calendar on [eventId]'s month with its sheet open. */
    fun event(eventId: String): String = "$URI?$EVENT_ARGUMENT=$eventId"

    const val EVENT_ARGUMENT = "event"
}
