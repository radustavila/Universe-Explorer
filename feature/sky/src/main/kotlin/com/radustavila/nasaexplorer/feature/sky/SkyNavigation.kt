package com.radustavila.nasaexplorer.feature.sky

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.SkyTonightDeepLink
import kotlinx.serialization.Serializable

/** Moon, planets, ISS passes and who is in space, for the user's location. */
@Serializable
data object SkyTonightRoute

fun NavController.navigateToSkyTonight(navOptions: NavOptions? = null) = navigate(SkyTonightRoute, navOptions)

/** The sky tonight widget opens this screen through [SkyTonightDeepLink]. */
fun NavGraphBuilder.skyTonightScreen(onBackClick: () -> Unit) {
    composable<SkyTonightRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = SkyTonightDeepLink.URI }),
    ) {
        SkyTonightScreen(onBackClick = onBackClick)
    }
}
