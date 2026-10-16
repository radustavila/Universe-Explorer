package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

/**
 * Loads [url] into a software [Bitmap] no larger than roughly [maxSizePx] on its longest side, or
 * returns `null` when it cannot be loaded.
 *
 * For surfaces drawn by another process - notifications and home screen widgets - which take a
 * bitmap rather than a Composable. Hardware bitmaps cannot be parcelled across that boundary, and
 * the size bound keeps the payload under the platform's limit. Goes through the application's image
 * loader, so the disk cache and the video frame decoder apply exactly as they do in-app.
 */
suspend fun Context.loadBitmap(url: String, maxSizePx: Int): Bitmap? {
    val request = ImageRequest.Builder(this)
        .data(url)
        .size(maxSizePx)
        .allowHardware(false)
        .build()

    val result = SingletonImageLoader.get(this).execute(request)
    return (result as? SuccessResult)?.image?.toBitmap()
}
