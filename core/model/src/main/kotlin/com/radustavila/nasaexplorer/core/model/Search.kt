package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate

/**
 * A local collection universal search looks in. Declaration order is the order groups are listed
 * in when their best matches rank the same.
 */
enum class SearchSource {
    /** Cached Astronomy Pictures of the Day (full-text index over title and explanation). */
    APOD,

    /** Saved items of every [FavoriteSource], by their title snapshot. */
    FAVORITES,

    /** NASA Image and Video Library items cached for the gallery rails or saved. */
    NASA_LIBRARY,

    /** Cached confirmed exoplanets, by planet and host star. */
    EXOPLANETS,

    /** Asteroids and comets passing Earth soon, by name or designation. */
    SMALL_BODIES,

    /** Open natural events on Earth (EONET). */
    EARTH_EVENTS,

    /** Upcoming rocket launches. */
    LAUNCHES,

    /** Sky events of the coming year, computed on the device. */
    SKY_EVENTS,
    /** Cached NASA news: stories, science articles, Images of the Day and videos. */
    NEWS,

    /** Comets near perihelion (F18), by name or designation. */
    COMETS,

    /** Cached NASA podcast episodes (F21), by title and description. */
    PODCASTS,
}

/** Where opening a [SearchHit] leads; `:app` maps each target to its feature's route. */
sealed interface SearchTarget {
    data class ApodEntry(val date: LocalDate) : SearchTarget
    data class NasaMediaItem(val nasaId: String) : SearchTarget
    data class ExoplanetEntry(val name: String) : SearchTarget
    data class SmallBody(val designation: String) : SearchTarget

    /** Events have no screen of their own; the events list shows them all. */
    data class EarthEventEntry(val id: String) : SearchTarget

    /** Launches are listed on the Today tab. */
    data class LaunchEntry(val id: String) : SearchTarget

    /** Opens the sky events calendar on the event (a `SkyEvent.id`). */
    data class SkyEventEntry(val id: String) : SearchTarget
    /** A cached news item; opens on its own screen, which links to the page or video. */
    data class NewsEntry(val id: String) : SearchTarget

    /** Opens the comet's details (F18). */
    data class CometEntry(val designation: String) : SearchTarget

    /** Opens the podcast episode's screen (F21). */
    data class PodcastEpisodeEntry(val id: String) : SearchTarget

    /** Opens the favourite in the screen of the feature it was saved from. */
    data class Favorite(val item: FavoriteItem) : SearchTarget
}

/**
 * One match of a universal search, already in display form so every group renders the same way.
 *
 * Texts are data from the source (names, titles, an excerpt), never sentences of the app's own: the
 * UI adds the localised wording, e.g. it formats [date].
 */
data class SearchHit(
    val source: SearchSource,
    /** Unique within [source]. */
    val id: String,
    val title: String,
    val target: SearchTarget,
    /** Secondary line from the data, e.g. a host star or a rocket. */
    val subtitle: String? = null,
    /** Excerpt of a longer text around the match, e.g. from an explanation. */
    val snippet: String? = null,
    val date: LocalDate? = null,
    val imageUrl: String? = null,
)
