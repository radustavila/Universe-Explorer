package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Opens a source's web page (a USGS event page, …) in a Custom Tab: the user's browser drawn over
 * the app in its dark colours, so back returns straight to the app. Without a browser that
 * supports them, the plain browser opens.
 */
@Composable
fun rememberCustomTabOpener(): (url: String) -> Unit {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    return remember(context, toolbarColor) { { url -> context.openInCustomTab(url, toolbarColor) } }
}

fun Context.openInCustomTab(url: String, toolbarColor: Int) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build())
        .build()
    try {
        intent.launchUrl(this, url.toUri())
    } catch (_: ActivityNotFoundException) {
        // No browser at all; nothing better to offer.
    }
}
