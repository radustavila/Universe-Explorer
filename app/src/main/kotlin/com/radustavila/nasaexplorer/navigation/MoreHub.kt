package com.radustavila.nasaexplorer.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.navigation.NavHostController
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.debug.navigateToDebug
import com.radustavila.nasaexplorer.ui.hub.HubEntry

/** Settings, Debug and secondary lists (e.g. every cached launch, F2). */
internal fun moreHubEntries(navController: NavHostController): List<HubEntry> = listOf(
    HubEntry(
        key = "debug",
        icon = Icons.Outlined.BugReport,
        titleRes = R.string.more_debug_title,
        descriptionRes = R.string.more_debug_description,
        onClick = { navController.navigateToDebug() },
    ),
)
