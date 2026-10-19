package com.radustavila.nasaexplorer.data.epic.prefetch

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Warms the image cache ahead of time. Best effort by contract: it never fails its caller. */
interface ImagePrefetcher {
    suspend fun prefetch(urls: List<String>)
}

/**
 * Downloads into the app's shared Coil disk cache, under the same keys the screens request, so a
 * later `AsyncImage(url)` is served from disk even with no connection. The memory cache is skipped:
 * nothing is about to be drawn.
 */
@Singleton
class CoilImagePrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageLoader: ImageLoader,
) : ImagePrefetcher {

    override suspend fun prefetch(urls: List<String>) {
        urls.forEach { url ->
            imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build(),
            )
        }
    }
}
