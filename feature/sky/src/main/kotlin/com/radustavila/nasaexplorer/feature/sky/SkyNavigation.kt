package com.radustavila.nasaexplorer.feature.sky

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Moon, planets, ISS passes and who is in space, for the user's location. */
@Serializable
data object SkyTonightRoute

fun NavController.navigateToSkyTonight(navOptions: NavOptions? = null) = navigate(SkyTonightRoute, navOptions)

fun NavGraphBuilder.skyTonightScreen(onBackClick: () -> Unit) {
    composable<SkyTonightRoute> {
        SkyTonightScreen(onBackClick = onBackClick)
    }
}
