package com.radustavila.nasaexplorer.core.model

import java.time.Duration
import java.time.Instant

/**
 * What NASA's Deep Space Network was doing at [observedAt], as its "DSN Now" feed reported it: the
 * three sites (Goldstone, Madrid, Canberra), each antenna's pointing and the spacecraft it serves.
 */
data class DsnSnapshot(
    /** The feed's own timestamp, not when the app downloaded it. */
    val observedAt: Instant,
    val sites: List<DsnSite>,
) {
    /** Every link that is sending or receiving right now, with the site it runs through. */
    val activeLinks: List<DsnSiteLink>
        get() = sites.flatMap { site ->
            site.dishes.flatMap { dish -> dish.links.filter(DsnLink::isActive).map { DsnSiteLink(site, dish, it) } }
        }
}

/** A link with the dish and site it runs through. */
data class DsnSiteLink(val site: DsnSite, val dish: DsnDish, val link: DsnLink)

/** One of the network's sites ("complexes"). */
data class DsnSite(
    /** The feed's id, e.g. `gdscc`. */
    val code: String,
    /** The site's proper name as the feed gives it, e.g. "Goldstone". */
    val name: String,
    val dishes: List<DsnDish>,
)

/** One antenna and what it is doing. */
data class DsnDish(
    /** The feed's id, e.g. `DSS43`. */
    val name: String,
    /** 70 or 34 for the network's two antenna sizes; null when the site list does not say. */
    val diameterMeters: Int?,
    /** Compass bearing the dish points at, degrees from north; null when the feed leaves it out. */
    val azimuthDegrees: Double?,
    /** Degrees above the horizon (90 is straight up, the dish's resting position). */
    val elevationDegrees: Double?,
    /** The feed's own description of the activity, e.g. "Spacecraft Telemetry, Tracking, and Command". */
    val activity: String?,
    /** The spacecraft the dish is assigned to; empty while it is idle or under maintenance. */
    val links: List<DsnLink>,
) {
    /** Sending or receiving to at least one spacecraft. */
    val isActive: Boolean get() = links.any(DsnLink::isActive)
}

/** A dish's connection to one spacecraft. */
data class DsnLink(
    val spacecraft: DsnSpacecraft,
    val upSignals: List<DsnSignal>,
    val downSignals: List<DsnSignal>,
    /** Earth to spacecraft, in km; null when neither the feed nor JPL Horizons knows it. */
    val distanceKm: Double?,
    val distanceSource: DistanceSource?,
) {
    /** A signal is on in either direction. */
    val isActive: Boolean get() = (upSignals + downSignals).any(DsnSignal::isActive)

    /** How long a radio signal takes one way; null without a distance. */
    val oneWayLightTime: Duration?
        get() = distanceKm?.let { DeepSpaceUnits.lightTime(it) }

    /** The fastest signal coming down right now, in bits per second; null when none carries data. */
    val downlinkRateBitsPerSecond: Double?
        get() = downSignals.filter(DsnSignal::isActive).mapNotNull(DsnSignal::dataRateBitsPerSecond)
            .filter { it > 0 }.maxOrNull()

    /** The fastest active uplink, in bits per second; often 0 (a carrier for ranging only). */
    val uplinkRateBitsPerSecond: Double?
        get() = upSignals.filter(DsnSignal::isActive).mapNotNull(DsnSignal::dataRateBitsPerSecond)
            .filter { it > 0 }.maxOrNull()

    enum class DistanceSource {
        /** The DSN feed's measured range. */
        DSN,

        /** JPL Horizons' predicted range for today, used when the feed gives none. */
        HORIZONS,
    }
}

data class DsnSpacecraft(
    /** The feed's code, upper case, e.g. `VGR2`. Stable: mission notes are keyed on it. */
    val code: String,
    /** "Voyager 2", from the network's spacecraft list; null when the list does not have it. */
    val name: String?,
    /** NAIF id (Voyager 2 is -32), as JPL Horizons uses; null when the feed leaves it out. */
    val naifId: Int?,
) {
    val displayName: String get() = name ?: code
}

/** One radio signal between a dish and a spacecraft. */
data class DsnSignal(
    /** Radio band letter: S, X, K or Ka. */
    val band: String?,
    val dataRateBitsPerSecond: Double?,
    val isActive: Boolean,
)

/**
 * A spacecraft far from Earth, as JPL Horizons predicts it, for the "Farthest from home" list.
 * Distances in AU are astronomical units: they are never converted to miles.
 */
data class DeepSpaceProbe(
    /** Horizons/NAIF id, e.g. -31 for Voyager 1. */
    val horizonsId: Int,
    /** Horizons' name, e.g. "Voyager 1". */
    val name: String,
    /** Distance from Earth's centre at [at], in AU. */
    val distanceAu: Double,
    /** How fast the distance grows, km/s (negative while approaching); null when unknown. */
    val rangeRateKmPerSecond: Double?,
    /** The instant Horizons computed the distance for. */
    val at: Instant,
) {
    /** The distance at [instant], moved on from [at] at the range rate (days only, so linear is exact enough). */
    fun distanceAuAt(instant: Instant): Double {
        val rate = rangeRateKmPerSecond ?: return distanceAu
        val seconds = Duration.between(at, instant).seconds.toDouble()
        return distanceAu + rate * seconds / DeepSpaceUnits.KM_PER_AU
    }

    fun distanceKmAt(instant: Instant): Double = distanceAuAt(instant) * DeepSpaceUnits.KM_PER_AU
}

/** "Right now: Voyager 2 is talking to Canberra": the farthest spacecraft in contact. */
data class DsnRightNow(
    val spacecraft: DsnSpacecraft,
    val siteName: String,
    val distanceKm: Double?,
    val observedAt: Instant,
)

/** Constants for distances in space; the IAU values Horizons itself prints. */
object DeepSpaceUnits {
    const val KM_PER_AU = 149_597_870.7
    const val LIGHT_SPEED_KM_PER_SECOND = 299_792.458

    fun lightTime(distanceKm: Double): Duration =
        Duration.ofMillis((distanceKm / LIGHT_SPEED_KM_PER_SECOND * MILLIS_PER_SECOND).toLong())

    fun auToKm(au: Double): Double = au * KM_PER_AU

    private const val MILLIS_PER_SECOND = 1_000
}
