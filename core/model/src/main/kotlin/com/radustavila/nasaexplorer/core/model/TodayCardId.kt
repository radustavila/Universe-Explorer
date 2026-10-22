package com.radustavila.nasaexplorer.core.model

/**
 * A card the user can show above the APOD feed on Today. Stored by [name], so an entry must never
 * be renamed: drop it instead, and stored lists skip names they do not know.
 *
 * Universal search is not a card: it always sits at the top.
 */
enum class TodayCardId {
    EARTH_TODAY,
    EARTH_EVENTS,
    NEXT_LAUNCHES,
    ISS_NOW,
    SKY_TONIGHT,
    SPACE_WEATHER,
    DSN_NOW,
    NASA_NEWS,
    ;

    companion object {
        /**
         * What Today shows until the user customizes it: the cards it always had, in that order,
         * then the latest from NASA (F14), the roadmap's Today feature.
         */
        val DEFAULT: List<TodayCardId> = listOf(EARTH_TODAY, EARTH_EVENTS, NEXT_LAUNCHES, NASA_NEWS)
    }
}
