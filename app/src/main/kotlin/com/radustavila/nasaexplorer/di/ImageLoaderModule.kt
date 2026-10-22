package com.radustavila.nasaexplorer.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.radustavila.nasaexplorer.core.network.http.forImages
import com.radustavila.nasaexplorer.core.offline.OfflineFavoriteImageInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Coil uses a client derived from the application's OkHttp client (same connection pool, logging
 * and rate limits, but no overall call timeout and no HTTP cache, see [forImages]) and keeps its own
 * on-disk cache, so images stay available offline just like the metadata in Room.
 *
 * Favourites do not depend on that cache: their offline copies are served first (see
 * [OfflineFavoriteImageInterceptor]).
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: dagger.Lazy<OkHttpClient>,
        offlineFavoriteImages: OfflineFavoriteImageInterceptor,
    ): ImageLoader {
        // Derived once, on the first image request, like the shared client itself.
        val imageClient by lazy { okHttpClient.get().forImages() }
        return ImageLoader.Builder(context)
            .components {
                add(offlineFavoriteImages)
                // Ordered: the video frame fetcher declines everything that is not a media stream, and
                // whatever it declines falls through to the network fetcher.
                add(VideoFrameFetcher.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private const val MEMORY_CACHE_PERCENT = 0.2
    private const val DISK_CACHE_BYTES = 128L * 1024 * 1024
}
