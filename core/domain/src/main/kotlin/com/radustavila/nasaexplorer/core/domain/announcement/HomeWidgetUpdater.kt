package com.radustavila.nasaexplorer.core.domain.announcement

/** The home screen widgets that show cached data from one source each, besides the APOD one. */
enum class HomeWidget {
    /** Current Kp, the forecast peak and the aurora chance at the saved place. */
    AURORA,

    /** The next rocket launch and its countdown. */
    NEXT_LAUNCH,

    /** Tonight's Moon and the next ISS pass over the saved place. */
    SKY_TONIGHT,

    /** The newest EPIC whole-Earth photo. */
    EARTH_TODAY,
}

/**
 * Re-renders every placed instance of a home screen widget from the local cache, like
 * [LatestApodWidgetUpdater]. Called when the data a widget shows has changed: a source refreshed, or
 * the saved place moved. Best effort and a no-op when the user has not added the widget.
 */
interface HomeWidgetUpdater {
    suspend fun update(widget: HomeWidget)
}
