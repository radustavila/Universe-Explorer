package com.radustavila.nasaexplorer.navigation

import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.feature.comets.navigateToCometDetails
import com.radustavila.nasaexplorer.feature.details.navigateToDetails
import com.radustavila.nasaexplorer.feature.earthevents.navigateToEarthEvents
import com.radustavila.nasaexplorer.feature.events.navigateToSkyEvents
import com.radustavila.nasaexplorer.feature.exoplanets.navigateToExoplanetDetails
import com.radustavila.nasaexplorer.feature.news.navigateToNewsItem
import com.radustavila.nasaexplorer.feature.news.navigateToPodcastEpisode
import com.radustavila.nasaexplorer.feature.spaceweather.navigateToSmallBody
import com.radustavila.nasaexplorer.feature.universe.navigateToNasaMedia
import com.radustavila.nasaexplorer.ui.navigateToTopLevel

/**
 * Opens a universal search result in its feature's screen. The search module cannot know those
 * screens, so `:app` resolves them, like [openFavorite]; a new search source wires its target here.
 */
internal fun NavHostController.openSearchTarget(target: SearchTarget) {
    when (target) {
        is SearchTarget.ApodEntry -> navigateToDetails(target.date)
        is SearchTarget.NasaMediaItem -> navigateToNasaMedia(target.nasaId)
        is SearchTarget.ExoplanetEntry -> navigateToExoplanetDetails(target.name)
        is SearchTarget.SmallBody -> navigateToSmallBody(target.designation)
        // Neither has a screen per item: events are listed together, launches on the Today tab.
        is SearchTarget.EarthEventEntry -> navigateToEarthEvents()
        is SearchTarget.LaunchEntry -> navigateToTopLevel(TopLevelDestination.TODAY)
        is SearchTarget.SkyEventEntry -> navigateToSkyEvents(target.id)
        is SearchTarget.NewsEntry -> navigateToNewsItem(target.id)
        is SearchTarget.CometEntry -> navigateToCometDetails(target.designation)
        is SearchTarget.EarthquakeEntry -> navigateToEarthEvents(earthquakeId = target.id)
        is SearchTarget.PodcastEpisodeEntry -> navigateToPodcastEpisode(target.id)
        is SearchTarget.Favorite -> openFavorite(target.item)
    }
}
