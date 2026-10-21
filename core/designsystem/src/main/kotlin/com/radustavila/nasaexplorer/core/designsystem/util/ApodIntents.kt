package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.model.ApodDeepLink
import java.time.LocalDate

/**
 * Opens the app on the details screen of [date], for surfaces that live outside the app.
 *
 * `null` only if the app has no launcher activity, which would be a packaging error.
 */
fun Context.apodDetailsIntent(date: LocalDate): Intent? = deepLinkIntent(ApodDeepLink.uriFor(date))

/**
 * Opens the app on the screen behind the in-app deep link [uri] (see `ApodDeepLink` and its
 * siblings in `:core:model`), for surfaces that live outside the app.
 *
 * The intent targets the launcher activity explicitly, so no public intent filter is needed for the
 * deep link; navigation resolves the URI on start. A fresh task keeps the back stack predictable:
 * back from the opened screen leads to the app's start screen, not to whatever was open before.
 *
 * `null` only if the app has no launcher activity, which would be a packaging error.
 */
fun Context.deepLinkIntent(uri: String): Intent? {
    val launcher = packageManager.getLaunchIntentForPackage(packageName)?.component ?: return null
    return Intent(Intent.ACTION_VIEW, uri.toUri())
        .setComponent(launcher)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
