// Named after the feature, as every module's navigation file is, not after its one route class.
@file:Suppress("MatchingDeclarationName")

package com.radustavila.nasaexplorer.feature.events

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import com.radustavila.nasaexplorer.core.model.SkyEventsDeepLink
import kotlinx.serialization.Serializable

/**
 * The sky events calendar, month by month. With [event] (a `SkyEvent.id`) it opens on that event's
 * month with its sheet open.
 */
@Serializable
data class SkyEventsRoute(val event: String? = null)

fun NavController.navigateToSkyEvents(eventId: String? = null, navOptions: NavOptions? = null) =
    navigate(SkyEventsRoute(eventId), navOptions)

/**
 * Reminder notifications open an event through [SkyEventsDeepLink]. Without a saved place the
 * screen offers [onSetLocation], which `:app` points at Sky tonight's location picker.
 */
fun NavGraphBuilder.skyEventsScreen(onBackClick: () -> Unit, onSetLocation: () -> Unit) {
    composable<SkyEventsRoute>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "${SkyEventsDeepLink.URI}?${SkyEventsDeepLink.EVENT_ARGUMENT}={event}" },
        ),
    ) {
        SkyEventsScreen(onBackClick = onBackClick, onSetLocation = onSetLocation)
    }
}
