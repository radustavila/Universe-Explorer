package com.radustavila.nasaexplorer.feature.universe.details

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.net.toUri
import com.radustavila.nasaexplorer.core.model.NasaMedia

/** The item's page on images.nasa.gov. */
internal val NasaMedia.webPageUrl: String
    get() = "https://images.nasa.gov/details/${Uri.encode(nasaId)}"

internal fun Context.shareNasaMedia(media: NasaMedia, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, media.title)
        .putExtra(Intent.EXTRA_TEXT, "${media.title}\n${media.webPageUrl}")
    startActivitySafely(Intent.createChooser(send, chooserTitle))
}

internal fun Context.openUrl(url: String) {
    startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri()))
}

/**
 * Hands the original file to the system's download manager, which shows its progress and keeps
 * going after the app is closed. It lands in the shared Downloads folder from Android 10 (no
 * permission needed); before that, in the app's own Downloads folder, which also needs none.
 * Without a download manager the file opens in the browser instead.
 */
internal fun Context.downloadOriginal(url: String, media: NasaMedia) {
    val fileName = url.toUri().lastPathSegment?.substringAfterLast('/') ?: "${media.nasaId}.jpg"
    val request = DownloadManager.Request(url.toUri())
        .setTitle(media.title)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    } else {
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
    }

    val manager = getSystemService(DownloadManager::class.java)
    try {
        checkNotNull(manager).enqueue(request)
    } catch (_: IllegalStateException) {
        openUrl(url)
    } catch (_: SecurityException) {
        openUrl(url)
    }
}

private fun Context.startActivitySafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can handle it; there is nothing better to offer.
    }
}
