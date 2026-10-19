package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One full-disk photo of Earth taken by the EPIC camera on the DSCOVR spacecraft, about 1.5 million
 * km away at the Sun-Earth L1 point. EPIC takes roughly a dozen of these a day as Earth turns
 * underneath it, so a day's photos played in order show one full rotation.
 *
 * The [identifier] is EPIC's own (`yyyyMMddHHmmss` of the capture) and is unique within a
 * [collection]; the same moment exists once as a natural-colour and once as an enhanced image.
 */
data class EpicImage(
    val identifier: String,
    val collection: EpicCollection,
    val capturedAt: Instant,
    val caption: String,
    /** The point on Earth directly below the spacecraft, i.e. the centre of the disc. */
    val centroid: GeoPoint,
    /** ~120 px, a few KB: what the rotation plays and what stays available offline. */
    val thumbnailUrl: String,
    /** 1080 px JPEG, a few hundred KB: what the paused and zoomed views load. */
    val imageUrl: String,
) {
    /** EPIC files its archive by UTC capture day. */
    val date: LocalDate get() = capturedAt.atOffset(ZoneOffset.UTC).toLocalDate()

    companion object {
        /** DSCOVR's first EPIC images; the archive holds nothing older. */
        val FIRST_CAPTURE_DATE: LocalDate = LocalDate.of(2015, 6, 13)
    }
}

/** The two image sets EPIC publishes for every capture. */
enum class EpicCollection(val apiValue: String) {
    /** Colour as the eye would see it. */
    NATURAL("natural"),

    /** Contrast-stretched to bring out land and clouds. */
    ENHANCED("enhanced"),
    ;

    companion object {
        fun fromApiValue(value: String?): EpicCollection? = entries.firstOrNull { it.apiValue == value }
    }
}

data class GeoPoint(val latitude: Double, val longitude: Double)
