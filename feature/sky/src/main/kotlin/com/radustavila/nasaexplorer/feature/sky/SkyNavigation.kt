package com.radustavila.nasaexplorer.feature.sky

import androidx.compose.runtime.Composable
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

/**
 * The sky tonight widget opens this screen through [SkyTonightDeepLink]. [nextInTheSky], [comets]
 * and [milkyWay] are other features' cards (the sky events calendar's, the bright comets' and the
 * Milky Way planner's), which `:app` passes in; they are shown below the planets.
 */
fun NavGraphBuilder.skyTonightScreen(
    onBackClick: () -> Unit,
    nextInTheSky: (@Composable () -> Unit)? = null,
    comets: (@Composable () -> Unit)? = null,
    milkyWay: (@Composable () -> Unit)? = null,
) {
    composable<SkyTonightRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = SkyTonightDeepLink.URI }),
    ) {
        SkyTonightScreen(
            onBackClick = onBackClick,
            nextInTheSky = nextInTheSky,
            comets = comets,
            milkyWay = milkyWay,
        )
    }
}
