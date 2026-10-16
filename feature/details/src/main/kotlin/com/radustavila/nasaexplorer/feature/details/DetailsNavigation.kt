package com.radustavila.nasaexplorer.feature.details

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.ApodDeepLink
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * The publication date is the entry's identity, so it is also the navigation argument. It travels as
 * an ISO string because navigation arguments must be primitives.
 */
@Serializable
data class DetailsRoute(val date: String)

fun NavController.navigateToDetails(date: LocalDate, navOptions: NavOptions? = null) =
    navigate(DetailsRoute(date.toString()), navOptions)

/**
 * Also reachable from outside the app as `nasaexplorer://apod/{date}` (see [ApodDeepLink]): the
 * new-entry notification and the home screen widget open the details screen directly.
 */
fun NavGraphBuilder.detailsScreen(onBackClick: () -> Unit) {
    composable<DetailsRoute>(
        deepLinks = listOf(navDeepLink<DetailsRoute>(basePath = ApodDeepLink.BASE_URI)),
    ) {
        DetailsScreen(onBackClick = onBackClick)
    }
}
