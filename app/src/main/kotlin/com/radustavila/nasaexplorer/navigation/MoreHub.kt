package com.radustavila.nasaexplorer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.BuildConfig
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.debug.navigateToDebug
import com.radustavila.nasaexplorer.feature.quiz.navigateToQuiz
import com.radustavila.nasaexplorer.feature.settings.navigateToAbout
import com.radustavila.nasaexplorer.feature.settings.navigateToSettings
import com.radustavila.nasaexplorer.ui.hub.HubEntry

/**
 * Settings, About, Debug, secondary lists (e.g. every cached launch, F2) and the space quiz (F28).
 * Debug (sync triggers, cache state) is a developer tool, so release builds leave it out.
 */
internal fun moreHubEntries(navController: NavHostController): List<HubEntry> = listOfNotNull(
    HubEntry(
        key = "settings",
        icon = Icons.Outlined.Settings,
        titleRes = R.string.more_settings_title,
        descriptionRes = R.string.more_settings_description,
        onClick = { navController.navigateToSettings() },
    ),
    HubEntry(
        key = "about",
        icon = Icons.Outlined.Info,
        titleRes = R.string.more_about_title,
        descriptionRes = R.string.more_about_description,
        onClick = { navController.navigateToAbout() },
    ),
    HubEntry(
        key = "debug",
        icon = Icons.Outlined.BugReport,
        titleRes = R.string.more_debug_title,
        descriptionRes = R.string.more_debug_description,
        onClick = { navController.navigateToDebug() },
    ).takeIf { BuildConfig.DEBUG },
    HubEntry(
        key = "quiz",
        icon = Icons.Outlined.Quiz,
        titleRes = R.string.more_quiz_title,
        descriptionRes = R.string.more_quiz_description,
        onClick = { navController.navigateToQuiz() },
    ),
)
