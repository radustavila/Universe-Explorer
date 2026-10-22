package com.radustavila.nasaexplorer.feature.settings

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.radustavila.nasaexplorer.feature.settings.about.AboutScreen
import com.radustavila.nasaexplorer.feature.settings.about.LicencesScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data object LicencesRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

fun NavController.navigateToAbout(navOptions: NavOptions? = null) = navigate(AboutRoute, navOptions)

fun NavController.navigateToLicences(navOptions: NavOptions? = null) = navigate(LicencesRoute, navOptions)

/**
 * @param locationSection the saved observer location and how to change it; owned by another
 * feature (Sky tonight) and passed in by the app, so this module does not depend on it.
 */
fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    locationSection: @Composable (Modifier) -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(onBackClick = onBackClick, locationSection = locationSection)
    }
}

fun NavGraphBuilder.aboutScreen(onBackClick: () -> Unit, onLicencesClick: () -> Unit) {
    composable<AboutRoute> {
        AboutScreen(onBackClick = onBackClick, onLicencesClick = onLicencesClick)
    }
}

/** @param librariesRes the raw resource the AboutLibraries plugin generates in the app. */
fun NavGraphBuilder.licencesScreen(onBackClick: () -> Unit, @RawRes librariesRes: Int) {
    composable<LicencesRoute> {
        LicencesScreen(onBackClick = onBackClick, librariesRes = librariesRes)
    }
}
