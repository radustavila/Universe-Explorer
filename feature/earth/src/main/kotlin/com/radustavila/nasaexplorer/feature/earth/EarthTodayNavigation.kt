package com.radustavila.nasaexplorer.feature.earth

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.model.EarthTodayDeepLink
import com.radustavila.nasaexplorer.core.model.EpicPhotoRef
import kotlinx.serialization.Serializable

/**
 * Whole-Earth photos from DSCOVR: the first screen of the Earth tab.
 *
 * Without arguments it shows the newest day. With them it opens one photo (a favourite): that
 * set, that day, starting on that frame. The ViewModel reads the arguments by these names.
 */
@Serializable
data class EarthTodayRoute(
    val collection: String? = null,
    val date: String? = null,
    val imageId: String? = null,
)

/** Opens one EPIC photo, e.g. from the Favourites tab. */
fun NavController.navigateToEpicPhoto(ref: EpicPhotoRef, navOptions: NavOptions? = null) = navigate(
    EarthTodayRoute(
        collection = ref.collection.apiValue,
        date = ref.date.toString(),
        imageId = ref.identifier,
    ),
    navOptions,
)

/**
 * @param onBackClick used when the screen was opened for one photo, i.e. pushed on top of another
 * screen; as the tab's start screen it has nowhere to go back to.
 * @param footer content below the rotation on the tab's start screen, such as entries leading to
 * the tab's other features. `:app` supplies it, so this module depends on none of them.
 *
 * The Earth today widget opens the newest day through [EarthTodayDeepLink].
 */
fun NavGraphBuilder.earthTodayScreen(
    onBackClick: () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    composable<EarthTodayRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = EarthTodayDeepLink.URI }),
    ) { entry ->
        val isPhotoLink = entry.toRoute<EarthTodayRoute>().imageId != null
        EarthTodayScreen(
            onBackClick = onBackClick.takeIf { isPhotoLink },
            footer = footer.takeUnless { isPhotoLink },
        )
    }
}
