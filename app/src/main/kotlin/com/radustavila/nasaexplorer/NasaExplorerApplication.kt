package com.radustavila.nasaexplorer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.radustavila.nasaexplorer.core.cache.CacheEvictionScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.data.earthevents.sync.EarthEventsSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NasaExplorerApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var cacheEvictionScheduler: CacheEvictionScheduler

    @Inject
    lateinit var earthEventsSyncScheduler: EarthEventsSyncScheduler

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        // Registering periodic sync is idempotent, so doing it on every start is safe and keeps the
        // schedule alive across app updates and reboots.
        syncScheduler.ensurePeriodicSync()
        cacheEvictionScheduler.ensurePeriodicEviction()
        earthEventsSyncScheduler.ensurePeriodicSync()
    }
}
