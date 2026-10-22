package com.radustavila.nasaexplorer.feature.settings.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.feature.settings.R

const val ABOUT_LIST_TEST_TAG = "about-list"
const val ABOUT_LICENCES_TEST_TAG = "about-licences"

fun aboutCreditTestTag(url: String) = "about-credit-$url"

@Composable
internal fun AboutScreen(
    onBackClick: () -> Unit,
    onLicencesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember(context) { context.appVersion() }
    AboutContent(
        version = version,
        onBackClick = onBackClick,
        onLicencesClick = onLicencesClick,
        onOpenUrl = context::openUrl,
        modifier = modifier,
    )
}

/** The installed app's name, version (e.g. "1.4.0") and build number. */
internal data class AppVersion(val label: String, val name: String, val code: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutContent(
    version: AppVersion?,
    onBackClick: () -> Unit,
    onLicencesClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxSize()
                    .testTag(ABOUT_LIST_TEST_TAG),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item(key = "app") {
                    Column(Modifier.padding(16.dp)) {
                        version?.let {
                            Text(
                                text = it.label,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        version?.let {
                            Text(
                                text = stringResource(R.string.about_version, it.name, it.code.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                item(key = "licences") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.about_licences_title)) },
                        supportingContent = { Text(stringResource(R.string.about_licences_description)) },
                        leadingContent = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
                        modifier = Modifier
                            .clickable(onClick = onLicencesClick)
                            .testTag(ABOUT_LICENCES_TEST_TAG),
                    )
                }
                item(key = "credits-title") {
                    Text(
                        text = stringResource(R.string.about_credits_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(DATA_CREDITS, key = { it.url }) { credit ->
                    val name = stringResource(credit.nameRes)
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(stringResource(credit.descriptionRes)) },
                        trailingContent = {
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.about_open_link, name),
                            )
                        },
                        modifier = Modifier
                            .clickable { onOpenUrl(credit.url) }
                            .testTag(aboutCreditTestTag(credit.url)),
                    )
                }
            }
        }
    }
}

internal fun Context.appVersion(): AppVersion? = try {
    val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    AppVersion(
        label = applicationInfo.loadLabel(packageManager).toString(),
        name = info.versionName.orEmpty(),
        code = PackageInfoCompat.getLongVersionCode(info),
    )
} catch (_: PackageManager.NameNotFoundException) {
    null
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser installed; nothing sensible to do.
    }
}

private val CONTENT_MAX_WIDTH = 640.dp
