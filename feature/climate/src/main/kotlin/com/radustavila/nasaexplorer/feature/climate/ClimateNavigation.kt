// Named after the feature, as every module's navigation file is, not after its route classes.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.climate

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import kotlinx.serialization.Serializable

/** One climate vital sign's full chart; [indicator] is a `ClimateIndicator.key`. */
@Serializable
data class ClimateRoute(val indicator: String)

fun NavController.navigateToClimate(indicator: ClimateIndicator, navOptions: NavOptions? = null) =
    navigate(ClimateRoute(indicator.key), navOptions)

/** The detail chart of one vital sign, registered inside the Earth tab's graph. */
fun NavGraphBuilder.climateScreen(onBackClick: () -> Unit) {
    composable<ClimateRoute> {
        ClimateScreen(onBackClick = onBackClick)
    }
}
