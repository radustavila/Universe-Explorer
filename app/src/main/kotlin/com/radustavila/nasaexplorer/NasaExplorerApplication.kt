package com.radustavila.nasaexplorer

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.StrictMode
import androidx.core.content.edit
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.radustavila.nasaexplorer.core.cache.CacheEvictionScheduler
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoriteImages
import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleSkyEventRemindersOnLocationChangeUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ScheduleAuroraAlertRefreshUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ScheduleStargazingAlertUseCase
import com.radustavila.nasaexplorer.data.comets.sync.CometsSyncScheduler
import com.radustavila.nasaexplorer.data.earthevents.sync.EarthEventsSyncScheduler
import com.radustavila.nasaexplorer.data.webb.sync.WebbScheduleSyncScheduler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
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
    lateinit var cometsSyncScheduler: Lazy<CometsSyncScheduler>

    @Inject
    lateinit var webbScheduleSyncScheduler: Lazy<WebbScheduleSyncScheduler>

    @Inject
    lateinit var exoplanetSyncScheduler: Lazy<ExoplanetSyncScheduler>

    @Inject
    lateinit var epicSyncScheduler: Lazy<EpicSyncScheduler>

    @Inject
    lateinit var offlineFavoriteImages: Lazy<OfflineFavoriteImages>

    @Inject
    lateinit var scheduleAuroraAlertRefresh: Lazy<ScheduleAuroraAlertRefreshUseCase>

    @Inject
    lateinit var scheduleStargazingAlert: Lazy<ScheduleStargazingAlertUseCase>

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    @Inject
    lateinit var rescheduleSkyEventReminders: Lazy<RescheduleSkyEventRemindersOnLocationChangeUseCase>

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
        if (BuildConfig.DEBUG) enableStrictMode()
        super.onCreate()
        applicationScope.launch(ioDispatcher) { registerPeriodicWorkOncePerApk() }
        // Favourites saved before offline copies existed get theirs; it guards itself to run once.
        applicationScope.launch(ioDispatcher) { offlineFavoriteImages.get().backfillOnce() }
        // Sky event reminders follow the saved place, whichever screen changes it.
        applicationScope.launch(ioDispatcher) { rescheduleSkyEventReminders.get().invoke() }
    }

    /**
     * Periodic work survives reboots and app restarts on its own, so it is registered once per
     * installed APK: after an update (which can change a job's constraints) or a fresh install.
     * The first registration initialises WorkManager (its database, schedulers and the force-stop
     * check), which the first frame does not need. The jobs are independent, so they register
     * concurrently.
     */
    private suspend fun registerPeriodicWorkOncePerApk() {
        val installedApk = installedApkKey()
        val startupPreferences = getSharedPreferences(STARTUP_PREFERENCES, MODE_PRIVATE)
        if (startupPreferences.getString(KEY_PERIODIC_WORK_REGISTERED_FOR, null) == installedApk) return
        coroutineScope {
            launch { syncScheduler.get().ensurePeriodicSync() }
            launch { cacheEvictionScheduler.get().ensurePeriodicEviction() }
            launch { earthEventsSyncScheduler.get().ensurePeriodicSync() }
            launch { exoplanetSyncScheduler.get().ensurePeriodicSync() }
            launch { epicSyncScheduler.get().ensurePeriodicSync() }
            launch { cometsSyncScheduler.get().ensurePeriodicSync() }
            launch { webbScheduleSyncScheduler.get().ensurePeriodicSync() }
            // Keeps the 30-minute space-weather job only while aurora alerts are on, which also drops
            // it from installs that registered it when the screen opened.
            launch { scheduleAuroraAlertRefresh.get().invoke() }
            // The clear-night check runs only while its alert is on.
            launch { scheduleStargazingAlert.get().invoke() }
        }
        startupPreferences.edit { putString(KEY_PERIODIC_WORK_REGISTERED_FOR, installedApk) }
    }

    /**
     * The version code alone stays the same across development installs, so the install time
     * is part of the key: every new APK registers the periodic work again.
     */
    private fun installedApkKey(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        return "${BuildConfig.VERSION_CODE}:${packageInfo.lastUpdateTime}"
    }

    /** Logs main-thread disk and network access and leaked resources in debug builds. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            // Not detectAll(): untagged sockets would flag every OkHttp connection.
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .detectCleartextNetwork()
                .penaltyLog()
                .build(),
        )
    }

    private companion object {
        const val STARTUP_PREFERENCES = "startup"
        const val KEY_PERIODIC_WORK_REGISTERED_FOR = "periodic_work_registered_for"
    }
}
