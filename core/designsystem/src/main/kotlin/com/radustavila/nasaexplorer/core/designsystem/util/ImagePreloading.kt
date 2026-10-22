package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/**
 * Downloads [url] into the image loader's disk cache ahead of time, so a later `NasaAsyncImage`
 * of it loads from disk, also offline. Only a token-size bitmap is decoded and it stays out of the
 * memory cache: nothing is about to be drawn, and a sequence of full-size frames would only push
 * each other out. Returns whether the image is available.
 */
suspend fun Context.preloadImage(url: String): Boolean {
    val request = ImageRequest.Builder(this)
        .data(url)
        .size(PRELOAD_DECODE_SIZE_PX)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build()
    return SingletonImageLoader.get(this).execute(request) is SuccessResult
}

/**
 * Decodes [url] into the memory cache the way a `NasaAsyncImage` filling a [sizePx] square asks
 * for it, so that image draws in its very first frame instead of showing its placeholder while it
 * decodes. For the next frame of an animation, just before it turns into view. Returns whether the
 * image is available.
 */
suspend fun Context.decodeImage(url: String, sizePx: Int): Boolean {
    val request = ImageRequest.Builder(this)
        .data(url)
        .size(sizePx)
        .build()
    return SingletonImageLoader.get(this).execute(request) is SuccessResult
}

/** Small enough to decode in a few milliseconds; the disk cache keeps the downloaded bytes whole. */
private const val PRELOAD_DECODE_SIZE_PX = 64
