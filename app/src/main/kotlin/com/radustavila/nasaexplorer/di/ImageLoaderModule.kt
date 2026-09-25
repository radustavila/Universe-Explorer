package com.radustavila.nasaexplorer.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Coil shares the application's OkHttp client (same timeouts, same logging) and keeps its own
 * on-disk cache, so images stay available offline just like the metadata in Room.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoaderModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: dagger.Lazy<OkHttpClient>,
    ): ImageLoader = ImageLoader.Builder(context)
        .components {
            // Ordered: the video frame fetcher declines everything that is not a media stream, and
            // whatever it declines falls through to the network fetcher.
            add(VideoFrameFetcher.Factory())
            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() }))
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

    private const val MEMORY_CACHE_PERCENT = 0.2
    private const val DISK_CACHE_BYTES = 128L * 1024 * 1024
}
