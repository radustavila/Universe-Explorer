package com.radustavila.nasaexplorer.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

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
    /**
     * 120 px, a few KB: stands in for a rotation frame while its HD image loads, or when it cannot
     * (offline). Every sync downloads the newest day's, so the rotation always plays offline.
     */
    val thumbnailUrl: String,
    /**
     * 1080 px JPEG, about 200 KB: what the rotation plays and the zoomed view shows. Syncs on an
     * unmetered network download the newest day's too, so it plays sharp offline as well.
     */
    val imageUrl: String,
) {
    /** EPIC files its archive by UTC capture day. */
    val date: LocalDate get() = capturedAt.atOffset(ZoneOffset.UTC).toLocalDate()

    /** Where to find this photo again, e.g. from a favourite saved long after the cache dropped it. */
    val ref: EpicPhotoRef get() = EpicPhotoRef(collection = collection, date = date, identifier = identifier)

    companion object {
        /** DSCOVR's first EPIC images; the archive holds nothing older. */
        val FIRST_CAPTURE_DATE: LocalDate = LocalDate.of(2015, 6, 13)

        /**
         * The title an EPIC favourite's snapshot stores: the capture instant in ISO-8601, raw data
         * the UI words in the user's language and clock. See [capturedAtOfFavoriteTitle].
         */
        fun favoriteTitle(capturedAt: Instant): String = capturedAt.toString()

        /**
         * Inverse of [favoriteTitle]; null for a title saved by older versions, which stored an
         * English sentence and are shown as they are.
         */
        fun capturedAtOfFavoriteTitle(title: String): Instant? = try {
            Instant.parse(title)
        } catch (_: DateTimeParseException) {
            null
        }
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

/**
 * Enough to find one EPIC photo again: the set, the archive day to load, and the photo within it.
 * Its [favoriteId] form is the item id of an EPIC favourite.
 */
data class EpicPhotoRef(
    val collection: EpicCollection,
    val date: LocalDate,
    val identifier: String,
) {
    /** `natural/2026-10-16/20261016002713`. */
    val favoriteId: String get() = "${collection.apiValue}/$date/$identifier"

    companion object {
        /** Inverse of [favoriteId]; `null` for anything that is not one. */
        fun fromFavoriteId(id: String): EpicPhotoRef? {
            val parts = id.split('/')
            if (parts.size != FAVORITE_ID_PARTS) return null
            val collection = EpicCollection.fromApiValue(parts[0]) ?: return null
            val date = try {
                LocalDate.parse(parts[1])
            } catch (_: DateTimeParseException) {
                return null
            }
            return parts[2].takeIf(String::isNotBlank)?.let { EpicPhotoRef(collection, date, it) }
        }

        private const val FAVORITE_ID_PARTS = 3
    }
}
