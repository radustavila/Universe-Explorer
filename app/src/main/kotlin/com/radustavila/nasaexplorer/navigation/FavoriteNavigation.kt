package com.radustavila.nasaexplorer.navigation

import androidx.navigation.NavHostController
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
 */
internal fun NavHostController.openFavorite(item: FavoriteItem) {
    when (item.source) {
        FavoriteSource.APOD -> item.itemId.toLocalDateOrNull()?.let { date -> navigateToDetails(date) }

        FavoriteSource.NASA_LIBRARY -> navigateToNasaMedia(item.itemId)
        FavoriteSource.EXOPLANET -> navigateToExoplanetDetails(item.itemId)

        FavoriteSource.MARS -> navigateToMarsPhoto(item.itemId)

        FavoriteSource.EPIC -> EpicPhotoRef.fromFavoriteId(item.itemId)?.let { ref -> navigateToEpicPhoto(ref) }
    }
}

private fun String.toLocalDateOrNull(): LocalDate? = try {
    LocalDate.parse(this)
} catch (_: DateTimeParseException) {
    null
}
