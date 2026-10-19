package com.radustavila.nasaexplorer.feature.earth

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/**
 * Whole-Earth photos from DSCOVR. Meant as the first screen of the Earth tab, and also reachable
 * from the Today card.
 */
@Serializable
data object EarthTodayRoute

fun NavController.navigateToEarthToday(navOptions: NavOptions? = null) =
    navigate(EarthTodayRoute, navOptions)

/**
 * @param onBackClick shows an up arrow when the screen was pushed (from the Today card); leave it
 * `null` where the screen is a tab's start destination.
 */
fun NavGraphBuilder.earthTodayScreen(onBackClick: (() -> Unit)? = null) {
    composable<EarthTodayRoute> {
        EarthTodayScreen(onBackClick = onBackClick)
    }
}
