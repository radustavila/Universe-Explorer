package com.radustavila.nasaexplorer.feature.news

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind

/**
 * What a tap on a news item does: an Image of the Day opens on its own screen ([onImageClick]), a
 * video in the YouTube app, a story in a Custom Tab over the app.
 */
@Composable
internal fun rememberNewsItemOpener(onImageClick: (id: String) -> Unit): (NewsItem) -> Unit {
    val openPage = rememberPageOpener()
    val onImage by rememberUpdatedState(onImageClick)
    return remember(openPage) {
        { item -> if (item.kind == NewsKind.IMAGE_OF_THE_DAY) onImage(item.id) else openPage(item) }
    }
}

/** Opens an item's page: a video in the YouTube app (or the browser), anything else in a Custom Tab. */
@Composable
internal fun rememberPageOpener(): (NewsItem) -> Unit {
    val context = LocalContext.current
    val toolbarColor = MaterialTheme.colorScheme.surface.toArgb()
    return remember(context, toolbarColor) {
        { item ->
            when (item.kind) {
                NewsKind.VIDEO -> context.openVideo(item.url)
                else -> context.openInCustomTab(item.url, toolbarColor)
            }
        }
    }
}

/**
 * NASA's page in a Custom Tab: the user's browser drawn over the app, in the app's dark colours, so
 * back returns straight to the list. Without a browser that supports them, the plain browser opens.
 */
internal fun Context.openInCustomTab(url: String, toolbarColor: Int) {
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

/**
 * Hands a video to whatever opens YouTube links: the YouTube app claims them, so it plays there,
 * with YouTube's own player and terms; otherwise the browser does. The app never plays or keeps
 * the video itself.
 */
internal fun Context.openVideo(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can open the link.
    }
}
