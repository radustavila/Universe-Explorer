package com.radustavila.nasaexplorer.core.model

/**
 * Where a favourite comes from. Together with the item's own id it identifies a saved item, so the
 * Favourites tab can show APOD entries, library images, rover photos and Earth photos side by side.
 *
 * [key] is persisted in the `favorite` table: never rename or reuse one. The sources on the roadmap
 * are declared up front so that feature sessions do not have to edit this file.
 */
enum class FavoriteSource(val key: String) {
    /** Astronomy Picture of the Day; item id is the ISO publication date. */
    APOD("apod"),

    /** DSCOVR EPIC whole-Earth photo; item id is [EpicPhotoRef.favoriteId] (`collection/date/identifier`). */
    EPIC("epic"),

    /** NASA Image and Video Library; item id is the library's `nasa_id`. */
    NASA_LIBRARY("nasa_library"),

    /** Mars rover raw image; item id is the image id of the raw-images API. */
    MARS("mars"),

    /** Confirmed exoplanet; item id is the archive's `pl_name`. */
    EXOPLANET("exoplanet"),
    ;

    companion object {
        fun fromKey(key: String): FavoriteSource? = entries.firstOrNull { it.key == key }
    }
}
