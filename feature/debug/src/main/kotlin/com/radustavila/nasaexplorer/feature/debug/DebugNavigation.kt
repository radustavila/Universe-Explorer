package com.radustavila.nasaexplorer.feature.debug

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DebugRoute

fun NavController.navigateToDebug(navOptions: NavOptions? = null) = navigate(DebugRoute, navOptions)

fun NavGraphBuilder.debugScreen() {
    composable<DebugRoute> {
        DebugScreen()
    }
}
