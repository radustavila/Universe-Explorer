package com.radustavila.nasaexplorer.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.radustavila.nasaexplorer.core.model.EpicPhotoRef
import com.radustavila.nasaexplorer.core.model.FavoriteItem
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.feature.details.navigateToDetails
import com.radustavila.nasaexplorer.feature.earth.navigateToEpicPhoto
import com.radustavila.nasaexplorer.feature.exoplanets.navigateToExoplanetDetails
import com.radustavila.nasaexplorer.feature.mars.navigateToMarsPhoto
import com.radustavila.nasaexplorer.feature.universe.navigateToNasaMedia
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Opens a favourite in the screen of the feature it came from. The Favourites module cannot know
 * those screens, so `:app` resolves them; a feature that adds a [FavoriteSource] wires it here.
 *
 * Works on the app's nav host (full screen) and on the Favourites detail pane's host, which
 * registers the screens [opensInFavoritesPane] accepts (see `NasaNavHost`).
 */
internal fun NavController.openFavorite(item: FavoriteItem, navOptions: NavOptions? = null) {
    when (item.source) {
        FavoriteSource.APOD -> item.itemId.toLocalDateOrNull()?.let { date -> navigateToDetails(date, navOptions) }

        FavoriteSource.NASA_LIBRARY -> navigateToNasaMedia(item.itemId, navOptions)
        FavoriteSource.EXOPLANET -> navigateToExoplanetDetails(item.itemId, navOptions)

        FavoriteSource.MARS -> navigateToMarsPhoto(item.itemId, navOptions)

        FavoriteSource.EPIC -> EpicPhotoRef.fromFavoriteId(item.itemId)?.let { ref -> navigateToEpicPhoto(ref, navOptions) }
    }
}

/**
 * An EPIC photo opens in the Earth tab's own screen (the rotation, with its tab), which is not a
 * detail screen and does not fit a pane; every other favourite can show beside the list.
 */
internal fun opensInFavoritesPane(item: FavoriteItem): Boolean = item.source != FavoriteSource.EPIC

private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (_: DateTimeParseException) {
    null
}
