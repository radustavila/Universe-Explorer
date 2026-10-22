package com.radustavila.nasaexplorer.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.model.ApodDeepLink
import com.radustavila.nasaexplorer.core.model.ApodWebLink
import java.time.LocalDate

/**
 * Turns a launch from an `apod.nasa.gov` link (see the manifest's intent filter) into the in-app
 * details deep link, before navigation reads the intent.
 */
internal object ApodWebLinks {

    /** Whether [intent] is a web link at all, as opposed to a launcher tap or an in-app deep link. */
    fun isWebLink(intent: Intent): Boolean =
        intent.action == Intent.ACTION_VIEW && intent.data?.scheme?.lowercase() in setOf("http", "https")

    /** The details deep link for the page [intent] views, or null when the app does not show that page. */
    fun inAppUriFor(intent: Intent, today: LocalDate): Uri? {
        if (!isWebLink(intent)) return null
        val date = ApodWebLink.dateOf(intent.dataString.orEmpty(), today) ?: return null
        return ApodDeepLink.uriFor(date).toUri()
    }

    /**
     * Hands [uri] to a browser rather than to this app: the selector only matches apps that handle
     * every web address, which this app's intent filter (one host, two pages) is not.
     *
     * @return false when there is no browser to hand it to.
     */
    fun openInBrowser(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.selector = Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
