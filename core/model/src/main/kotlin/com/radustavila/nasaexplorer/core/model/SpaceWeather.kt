package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import kotlin.math.roundToInt

/** Where a 3-hour Kp value comes from in NOAA SWPC's planetary K-index product. */
enum class KpKind {
    /** Measured, final. */
    OBSERVED,

    /** Measured so far in a slot that has not ended, or nowcast. */
    ESTIMATED,

    /** Forecast by SWPC forecasters. */
    PREDICTED,
}

/**
 * The planetary K-index for one 3-hour slot starting at [start]: how disturbed Earth's magnetic
 * field is on a 0–9 scale. [kp] keeps NOAA's thirds (4.67 is "5-").
 */
data class KpReading(
    val start: Instant,
    val kp: Double,
    val kind: KpKind,
) {
    val end: Instant get() = start.plus(SLOT)

    /** The whole Kp number NOAA reports this value as (4.67 → 5). */
    val level: Int get() = Kp.level(kp)

    val stormLevel: GeomagneticStorm? get() = GeomagneticStorm.forKp(kp)

    companion object {
        val SLOT: java.time.Duration = java.time.Duration.ofHours(3)
    }
}

/** The 1-minute Kp estimate, what the gauge shows as "now". */
data class CurrentKp(val time: Instant, val kp: Double) {
    val level: Int get() = Kp.level(kp)
    val stormLevel: GeomagneticStorm? get() = GeomagneticStorm.forKp(kp)
}

object Kp {
    const val MAX = 9

    /** NOAA rounds to the nearest whole Kp when it assigns a G-scale: 4.67 ("5-") is a G1 storm. */
    fun level(kp: Double): Int = kp.roundToInt().coerceIn(0, MAX)
}

/** NOAA's geomagnetic storm scale. */
enum class GeomagneticStorm(val minKp: Int) {
    G1(5),
    G2(6),
    G3(7),
    G4(8),
    G5(9),
    ;

    companion object {
        fun forKp(kp: Double): GeomagneticStorm? = forLevel(Kp.level(kp))

        fun forLevel(level: Int): GeomagneticStorm? = entries.lastOrNull { level >= it.minKp }
    }
}

/** A solar flare from DONKI. [classType] is the GOES X-ray class, e.g. "M1.4" or "X2.0". */
data class SolarFlare(
    val id: String,
    val classType: String,
    val beginTime: Instant,
    val peakTime: Instant?,
    val endTime: Instant?,
    val sourceLocation: String?,
    val activeRegion: Int?,
    val link: String?,
) {
    /** A, B, C, M or X; each letter is ten times stronger than the one before. */
    val classLetter: Char? get() = classType.firstOrNull()?.uppercaseChar()?.takeIf { it in FLARE_CLASSES }

    private companion object {
        const val FLARE_CLASSES = "ABCMX"
    }
}

/** A coronal mass ejection from DONKI, with its most accurate analysis. */
data class CoronalMassEjection(
    val id: String,
    val startTime: Instant,
    val speedKmPerSecond: Double?,
    val halfAngleDegrees: Double?,
    val sourceLocation: String?,
    val note: String?,
    /** When the WSA-ENLIL model expects the shock to reach Earth; null when it misses Earth. */
    val earthArrival: Instant?,
    /** Whether the model expects only a glancing blow at Earth. */
    val isEarthGlancingBlow: Boolean,
    val link: String?,
)

/**
 * NOAA's OVATION aurora nowcast: the probability (0–100 %) of visible aurora on a one-degree grid.
 * Longitudes run 0..359 east, latitudes -90..90.
 */
class AuroraForecast(
    val observedAt: Instant,
    val forecastFor: Instant,
    private val probabilities: ByteArray,
) {
    init {
        require(probabilities.size == LONGITUDES * LATITUDES) { "expected a 360 x 181 grid" }
    }

    /** Probability in percent at a grid point; [longitude] 0..359 east, [latitude] -90..90. */
    fun probabilityAt(longitude: Int, latitude: Int): Int =
        probabilities[index(Math.floorMod(longitude, LONGITUDES), latitude.coerceIn(-90, 90))].toInt()

    /** The raw grid, longitude-major, for storage. */
    fun toBytes(): ByteArray = probabilities.copyOf()

    override fun equals(other: Any?): Boolean =
        other is AuroraForecast && observedAt == other.observedAt && forecastFor == other.forecastFor &&
            probabilities.contentEquals(other.probabilities)

    override fun hashCode(): Int =
        31 * (31 * observedAt.hashCode() + forecastFor.hashCode()) + probabilities.contentHashCode()

    override fun toString(): String = "AuroraForecast(observedAt=$observedAt, forecastFor=$forecastFor)"

    companion object {
        const val LONGITUDES = 360
        const val LATITUDES = 181

        fun index(longitude: Int, latitude: Int): Int = longitude * LATITUDES + (latitude + 90)
    }
}

/** What the user chose for aurora alerts. */
data class AuroraAlertSettings(
    val enabled: Boolean = false,
    /** Where the user watches from; alerts need it to know which Kp reaches them. */
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /** The smallest Kp at which aurora may be seen from the saved place; null without a place. */
    val minimumKp: Int?
        get() = if (latitude != null && longitude != null) AuroraVisibility.minimumKp(latitude, longitude) else null
}

/** One alert: aurora may be visible from the user's place from [startsAt]. */
data class AuroraAlert(
    val startsAt: Instant,
    val expectedKp: Double,
    val threshold: Int,
)
