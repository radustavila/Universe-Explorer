package com.radustavila.nasaexplorer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.radustavila.nasaexplorer.core.cache.CacheEvictionScheduler
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.data.earthevents.sync.EarthEventsSyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class NasaExplorerApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

    // The collaborators are Lazy: the application is injected before the first frame, and none of
    // them is needed for that frame.
    @Inject
    lateinit var workerFactory: Lazy<HiltWorkerFactory>

    @Inject
    lateinit var syncScheduler: Lazy<SyncScheduler>

    @Inject
    lateinit var cacheEvictionScheduler: Lazy<CacheEvictionScheduler>

    @Inject
    lateinit var earthEventsSyncScheduler: Lazy<EarthEventsSyncScheduler>

    @Inject
    lateinit var exoplanetSyncScheduler: Lazy<ExoplanetSyncScheduler>

    @Inject
    lateinit var epicSyncScheduler: Lazy<EpicSyncScheduler>

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    @Dispatcher(AppDispatcher.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory.get())
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override fun onCreate() {
        super.onCreate()
        // Registering periodic sync is idempotent, so doing it on every start is safe and keeps the
        // schedule alive across app updates and reboots. It runs off the main thread: the first
        // registration initialises WorkManager (its database, schedulers and the force-stop check),
        // which the first frame does not need.
        applicationScope.launch(ioDispatcher) {
            syncScheduler.get().ensurePeriodicSync()
            cacheEvictionScheduler.get().ensurePeriodicEviction()
            earthEventsSyncScheduler.get().ensurePeriodicSync()
            exoplanetSyncScheduler.get().ensurePeriodicSync()
            epicSyncScheduler.get().ensurePeriodicSync()
        }
    }
}
