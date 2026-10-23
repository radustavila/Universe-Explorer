package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate

/** A point on the Moon in the mean Earth / polar axis frame: east longitude −180..180, degrees. */
data class SelenographicPoint(val latitude: Double, val longitude: Double)

/** Who flew a lunar landing: the map groups and filters its markers by this. */
enum class LunarProgramme {
    APOLLO,
    LUNA,
    SURVEYOR,
    CHANGE,
    CHANDRAYAAN,
    SLIM,

    /** NASA's Commercial Lunar Payload Services landers (Intuitive Machines, Firefly, …). */
    CLPS,
}

/** What came down at a landing site. */
enum class LunarLandingKind {
    CREWED,
    LANDER,
    ROVER,
    SAMPLE_RETURN,
}

/**
 * Where a mission landed on the Moon. Names are proper names from the source table; the app words
 * everything else.
 */
data class LunarLandingSite(
    /** Stable id, e.g. `apollo-11`. */
    val id: String,
    val mission: String,
    val programme: LunarProgramme,
    val kind: LunarLandingKind,
    /** Landing date (UTC). */
    val landedOn: LocalDate,
    val position: SelenographicPoint,
    /** The named feature it landed in or near, e.g. Mare Tranquillitatis. */
    val region: String,
    /** The astronauts who landed, for crewed missions. */
    val crew: List<String> = emptyList(),
    /** The rover it carried, if any. */
    val rover: String? = null,
    /** NSSDCA master catalogue id (COSPAR), when its record is known. */
    val nssdcaId: String? = null,
) {
    /** NSSDCA's record of the lander, or null. */
    val nssdcaUrl: String?
        get() = nssdcaId?.let { "https://nssdc.gsfc.nasa.gov/nmc/spacecraft/display.action?id=$it" }
}

/**
 * Where the Sun stands over the Moon at [instant]: the subsolar point, from which the terminator
 * (the line between lunar day and night) follows.
 */
data class MoonIllumination(
    val instant: Instant,
    val subsolarPoint: SelenographicPoint,
)

/** The on-demand pack with the Moon map's zoom 5–6 tiles (F25). */
sealed interface MoonTilesPack {
    /** Not on the device; [sizeBytes] is the download size when the store knows it. */
    data class NotDownloaded(val sizeBytes: Long?) : MoonTilesPack

    /** Queued or downloading; [totalBytes] is 0 until the store knows it. */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : MoonTilesPack {
        val fraction: Float?
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    /** The store is waiting for Wi-Fi before it downloads. */
    data object WaitingForWifi : MoonTilesPack

    /** On the device: tiles are read from [directory] (`{z}/{x}/{y}.jpg`). */
    data class Installed(val directory: String, val sizeBytes: Long) : MoonTilesPack

    /** The last download failed; it can be tried again. */
    data class Failed(val reason: MoonTilesFailure) : MoonTilesPack

    /** No Play Store asset delivery here (e.g. an APK installed by hand): only zoom 0–4. */
    data object Unavailable : MoonTilesPack
}

/** Why the deeper Moon tiles did not download. */
enum class MoonTilesFailure {
    NO_CONNECTION,
    NOT_ENOUGH_STORAGE,
    OTHER,
}
