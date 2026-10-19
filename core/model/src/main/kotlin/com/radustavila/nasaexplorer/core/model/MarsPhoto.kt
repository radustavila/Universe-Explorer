package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * One raw image sent back by a Mars rover, as published on mars.nasa.gov a few hours after it
 * reaches Earth: unprocessed, often a subframe or an engineering camera's view.
 *
 * [id] is the raw-images API's image id (e.g. `5048MH0009060011804545C00_DXXX`), unique across
 * rovers; it is also the photo's favourite id.
 */
data class MarsPhoto(
    val id: String,
    val rover: MarsRover,
    /** Mars day of the mission the photo was taken on. */
    val sol: Int,
    /** The camera as the API names it, e.g. `MAHLI` or `NAVCAM_LEFT`. */
    val cameraId: String,
    /** The camera's full name, e.g. "Mars Hand Lens Imager (MAHLI)". */
    val cameraName: String,
    val takenAt: Instant?,
    /** Largest JPEG/PNG rendition, for the full-screen viewer. */
    val imageUrl: String,
    /** Smaller rendition for rails and grids, when the feed offers one. */
    val thumbnailUrl: String? = null,
    /** The feed's caption, when it has one. */
    val description: String? = null,
    val credit: String? = null,
) {
    /** Image to draw in a list. */
    val previewUrl: String get() = thumbnailUrl ?: imageUrl

    /** A self-contained name, e.g. for a favourite: "Curiosity, sol 5048: Mars Hand Lens Imager (MAHLI)". */
    val title: String get() = "${rover.displayName}, sol $sol: $cameraName"
}

/**
 * The rovers whose raw images the app follows.
 *
 * [key] is persisted: never rename or reuse one.
 */
enum class MarsRover(val key: String, val displayName: String) {
    CURIOSITY("curiosity", "Curiosity"),
    PERSEVERANCE("perseverance", "Perseverance"),
    ;

    companion object {
        fun fromKey(key: String?): MarsRover? = entries.firstOrNull { it.key == key }
    }
}
