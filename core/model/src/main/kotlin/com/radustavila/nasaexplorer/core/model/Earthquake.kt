package com.radustavila.nasaexplorer.core.model

import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One earthquake from the U.S. Geological Survey's feeds (public domain; credit "U.S. Geological
 * Survey"). [place] is USGS's own wording, e.g. "61 km WNW of Leni, Italy"; [EarthquakePlace] splits
 * it so the distance can be shown in the user's units.
 */
data class Earthquake(
    /** USGS event id, e.g. `us6000u4bk`. */
    val id: String,
    val magnitude: Double,
    val place: String?,
    val time: Instant,
    /** The epicentre. */
    val position: GeoCoordinate,
    val depthKm: Double,
    /** USGS event page, opened for the full details. */
    val url: String,
    /** How many people reported feeling it ("Did You Feel It?"); null when nobody did. */
    val feltReports: Int? = null,
    /** PAGER's estimate of losses; null for most quakes, which are too small or far from people. */
    val alertLevel: EarthquakeAlertLevel? = null,
    /** USGS's flag for a large quake in an oceanic region: check the official tsunami warnings. */
    val tsunami: Boolean = false,
    /** USGS's 0–1000+ significance, from magnitude, felt reports and estimated impact. */
    val significance: Int = 0,
) {
    val depth: EarthquakeDepth get() = EarthquakeDepth.of(depthKm)

    /** As USGS's own "significant earthquakes" feed counts them. */
    val isSignificant: Boolean get() = significance >= SIGNIFICANT_FROM

    val parsedPlace: EarthquakePlace? get() = place?.let(EarthquakePlace::parse)

    companion object {
        const val SIGNIFICANT_FROM = 600
    }
}

/** PAGER alert levels, from no expected damage to extensive losses. [key] is USGS's word, as stored. */
enum class EarthquakeAlertLevel(val key: String) {
    GREEN("green"),
    YELLOW("yellow"),
    ORANGE("orange"),
    RED("red"),
    ;

    companion object {
        fun fromKey(key: String?): EarthquakeAlertLevel? = entries.firstOrNull { it.key == key?.lowercase() }
    }
}

/** The usual three classes of focal depth, which the map colours quakes by. */
enum class EarthquakeDepth {
    /** 0–70 km: most quakes, and the most damaging for their size. */
    SHALLOW,

    /** 70–300 km. */
    INTERMEDIATE,

    /** Below 300 km, in subducting slabs. */
    DEEP,
    ;

    companion object {
        const val INTERMEDIATE_FROM_KM = 70.0
        const val DEEP_FROM_KM = 300.0

        fun of(depthKm: Double): EarthquakeDepth = when {
            depthKm >= DEEP_FROM_KM -> DEEP
            depthKm >= INTERMEDIATE_FROM_KM -> INTERMEDIATE
            else -> SHALLOW
        }
    }
}

/**
 * USGS's place text split up: "61 km WNW of Leni, Italy" is 61 km, `WNW`, "Leni, Italy". Places
 * without a distance ("central Mid-Atlantic Ridge") only have a [name].
 */
data class EarthquakePlace(
    val name: String,
    val distanceKm: Double? = null,
    /** A 16-point compass abbreviation such as `WNW`. */
    val direction: String? = null,
) {
    companion object {
        private val WITH_DISTANCE = Regex("""^\s*(\d+(?:\.\d+)?)\s*km\s+([NESW]{1,3})\s+of\s+(.+?)\s*$""")

        fun parse(place: String): EarthquakePlace? {
            val trimmed = place.trim().takeIf(String::isNotEmpty) ?: return null
            return WITH_DISTANCE.matchEntire(trimmed)?.destructured?.let { (distance, direction, name) ->
                EarthquakePlace(name = name, distanceKm = distance.toDouble(), direction = direction)
            } ?: EarthquakePlace(name = trimmed)
        }
    }
}

/** Which quakes the events map shows; one at a time, or none. [key] is saved, never rename one. */
enum class EarthquakeFilter(val key: String, val minimumMagnitude: Double?, val window: Duration) {
    /** USGS's default feed: magnitude 2.5 and up, past week. */
    MAGNITUDE_2_5("m2_5", minimumMagnitude = 2.5, window = Duration.ofDays(DAYS_IN_WEEK)),

    /** Magnitude 4.5 and up, past week: felt widely, about a hundred a week. */
    MAGNITUDE_4_5("m4_5", minimumMagnitude = 4.5, window = Duration.ofDays(DAYS_IN_WEEK)),

    /** USGS's significant earthquakes of the past month. */
    SIGNIFICANT("significant", minimumMagnitude = null, window = Duration.ofDays(DAYS_IN_MONTH)),
    ;

    fun matches(quake: Earthquake, now: Instant): Boolean {
        val recent = !quake.time.isBefore(now.minus(window))
        return recent && if (minimumMagnitude == null) quake.isSignificant else quake.magnitude >= minimumMagnitude
    }

    companion object {
        val DEFAULT = MAGNITUDE_2_5

        fun fromKey(key: String?): EarthquakeFilter? = entries.firstOrNull { it.key == key }
    }
}

/** USGS's feed windows: `_week` and `_month`. */
private const val DAYS_IN_WEEK = 7L
private const val DAYS_IN_MONTH = 30L

/**
 * A quake worth a notification: [earthquake] struck [distanceKm] from the saved place. [isNearby]
 * when it passed the magnitude rule; otherwise it only passed for its tsunami flag.
 */
data class EarthquakeAlert(
    val earthquake: Earthquake,
    val distanceKm: Double,
    val isNearby: Boolean,
)

/** Great-circle distance on a spherical Earth: within 0.5 %, plenty for "within 500 km". */
fun GeoCoordinate.distanceKmTo(other: GeoCoordinate): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * MEAN_EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private const val MEAN_EARTH_RADIUS_KM = 6371.0088

/**
 * In-app address of the Live Earth events map, opened by the strong-quake alert, like
 * [ApodDeepLink]. `?earthquake=` opens it on that quake.
 */
object EarthEventsDeepLink {
    const val URI = "nasaexplorer://earthevents"
    const val EARTHQUAKE_ARGUMENT = "earthquake"

    fun uriFor(earthquakeId: String): String = "$URI?$EARTHQUAKE_ARGUMENT=$earthquakeId"
}
