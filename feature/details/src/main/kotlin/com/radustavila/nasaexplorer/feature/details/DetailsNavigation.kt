package com.radustavila.nasaexplorer.feature.details

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
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

fun NavGraphBuilder.detailsScreen(onBackClick: () -> Unit) {
    composable<DetailsRoute> {
        DetailsScreen(onBackClick = onBackClick)
    }
}
