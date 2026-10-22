package com.radustavila.nasaexplorer.core.model

/**
 * Every remote feed the app keeps a local copy of, as far as synchronisation bookkeeping is
 * concerned (last success, last error, next allowed refresh).
 *
 * [key] is persisted: never rename or reuse one. Sources from the roadmap are declared up front so
 * that feature sessions do not have to edit this file; add an entry only for a source not listed.
 */
enum class SyncSource(val key: String) {
    /** Astronomy Picture of the Day, science.nasa.gov `apod-basic`. */
    APOD("apod"),

    /** DSCOVR EPIC whole-Earth photos. */
    EPIC("epic"),

    /** Launch Library 2 upcoming launches. */
    LAUNCHES("launch_library"),

    /** NASA Image and Video Library. */
    NASA_LIBRARY("nasa_library"),

    /** NASA Exoplanet Archive TAP. */
    EXOPLANETS("exoplanet_archive"),

    /** Mars rover raw images. */
    MARS("mars_raw_images"),

    /** NASA EONET natural events. */
    EONET("eonet"),

    /** NOAA SWPC Kp index, aurora and alerts. */
    SPACE_WEATHER("noaa_swpc"),

    /** DONKI flares, CMEs and storms. */
    DONKI("donki"),

    /** JPL close approaches, Sentry and fireballs. */
    ASTEROIDS("jpl_ssd"),

    /** DSN Now live antenna state, polled only while its screen or Today card is visible. */
    DSN_NOW("dsn_now"),

    /** DSN Now site and spacecraft names; changes a few times a year. */
    DSN_CONFIG("dsn_config"),

    /** JPL Horizons distances of far spacecraft (Voyager 1 and 2, New Horizons, …). */
    HORIZONS("jpl_horizons"),

    /** CelesTrak orbital elements. */
    CELESTRAK("celestrak"),

    /**
     * Launch Library 2 astronauts in space. Same host and hourly budget as [LAUNCHES], but its own
     * interval: the crew list changes a few times a year, the launch list several times a day.
     */
    PEOPLE_IN_SPACE("launch_library_astronauts"),
    ;

    companion object {
        fun fromKey(key: String): SyncSource? = entries.firstOrNull { it.key == key }
    }
}
