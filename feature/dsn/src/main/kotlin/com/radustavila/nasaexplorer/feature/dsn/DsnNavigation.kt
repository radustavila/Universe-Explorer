// Named for the feature, like every other feature's navigation file.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.dsn

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object DsnRoute

fun NavController.navigateToDsn(navOptions: NavOptions? = null) = navigate(DsnRoute, navOptions)

/** The Deep Space Network screen; registered inside the Universe tab's graph. */
fun NavGraphBuilder.dsnScreen(onBackClick: () -> Unit) {
    composable<DsnRoute> {
        DsnScreen(onBackClick = onBackClick)
    }
}
