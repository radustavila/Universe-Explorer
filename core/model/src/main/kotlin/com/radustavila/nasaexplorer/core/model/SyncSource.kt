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

    /** CelesTrak orbital elements of the ISS. */
    CELESTRAK("celestrak"),

    /** CelesTrak orbital elements of the Hubble Space Telescope. */
    CELESTRAK_HUBBLE("celestrak_hubble"),

    /** CelesTrak orbital elements of the Tiangong space station. */
    CELESTRAK_TIANGONG("celestrak_tiangong"),

    /** CelesTrak `last-30-days` group, for Starlink trains. */
    CELESTRAK_STARLINK("celestrak_last_30_days"),

    /** CelesTrak `visual` group: the brightest satellites. */
    CELESTRAK_VISUAL("celestrak_visual"),

    /**
     * Launch Library 2 astronauts in space. Same host and hourly budget as [LAUNCHES], but its own
     * interval: the crew list changes a few times a year, the launch list several times a day.
     */
    PEOPLE_IN_SPACE("launch_library_astronauts"),

    /** Solar Dynamics Observatory latest pictures of the Sun. */
    SDO("sdo"),

    /** MET Norway Locationforecast cloud cover, for the stargazing forecast. */
    MET_NORWAY("met_norway"),

    /** NASA news (F14), one source per feed: nasa.gov stories. */
    NASA_NEWS("nasa_news"),

    /** nasa.gov news releases. */
    NASA_NEWS_RELEASES("nasa_news_releases"),

    /** science.nasa.gov articles. */
    NASA_SCIENCE_NEWS("nasa_science_news"),

    /** NASA Image of the Day. */
    NASA_IMAGE_OF_THE_DAY("nasa_image_of_the_day"),

    /** NASA's YouTube channel feed. */
    NASA_YOUTUBE("nasa_youtube"),

    /** JPL SBDB comet candidates and their Horizons ephemerides (F18), refreshed together daily. */
    COMETS("jpl_comets"),

    /** USGS earthquake summary feeds (F19): magnitude 2.5+ of the past week, significant of the month. */
    EARTHQUAKES("usgs_earthquakes"),

    /** NASA podcasts (F21), one source per show: Houston We Have a Podcast. */
    NASA_PODCAST_HOUSTON("nasa_podcast_houston"),

    /** NASA's Curious Universe. */
    NASA_PODCAST_CURIOUS_UNIVERSE("nasa_podcast_curious_universe"),

    /** Small Steps, Giant Leaps. */
    NASA_PODCAST_SMALL_STEPS("nasa_podcast_small_steps"),

    /** STScI's weekly JWST observing schedule (F24): the index page and the newest week's file, daily. */
    JWST_SCHEDULE("stsci_jwst_schedule"),
    ;

    companion object {
        fun fromKey(key: String): SyncSource? = entries.firstOrNull { it.key == key }
    }
}
