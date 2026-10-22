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
import com.radustavila.nasaexplorer.feature.settings.today.CustomizeTodayScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data object LicencesRoute

/** Which cards Today shows, and their order; reached from Settings and from Today itself. */
@Serializable
data object CustomizeTodayRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsRoute, navOptions)

fun NavController.navigateToAbout(navOptions: NavOptions? = null) = navigate(AboutRoute, navOptions)

fun NavController.navigateToLicences(navOptions: NavOptions? = null) = navigate(LicencesRoute, navOptions)

fun NavController.navigateToCustomizeToday(navOptions: NavOptions? = null) = navigate(CustomizeTodayRoute, navOptions)

/**
 * @param locationSection the saved observer location and how to change it; owned by another
 * feature (Sky tonight) and passed in by the app, so this module does not depend on it.
 */
fun NavGraphBuilder.settingsScreen(
    onBackClick: () -> Unit,
    onCustomizeTodayClick: () -> Unit,
    locationSection: @Composable (Modifier) -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onBackClick = onBackClick,
            onCustomizeTodayClick = onCustomizeTodayClick,
            locationSection = locationSection,
        )
    }
}

fun NavGraphBuilder.customizeTodayScreen(onBackClick: () -> Unit) {
    composable<CustomizeTodayRoute> {
        CustomizeTodayScreen(onBackClick = onBackClick)
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
