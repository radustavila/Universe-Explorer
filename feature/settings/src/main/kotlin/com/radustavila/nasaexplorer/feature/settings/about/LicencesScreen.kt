package com.radustavila.nasaexplorer.feature.settings.about

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.radustavila.nasaexplorer.feature.settings.R

const val LICENCES_LIST_TEST_TAG = "licences-list"

/**
 * Every library in the release build with its licence, from the list the AboutLibraries plugin
 * generates at build time ([librariesRes], in the app); tapping one shows the licence text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicencesScreen(
    onBackClick: () -> Unit,
    @RawRes librariesRes: Int,
    modifier: Modifier = Modifier,
) {
    val libraries by produceLibraries(librariesRes)
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_licences_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .testTag(LICENCES_LIST_TEST_TAG),
        )
    }
}
