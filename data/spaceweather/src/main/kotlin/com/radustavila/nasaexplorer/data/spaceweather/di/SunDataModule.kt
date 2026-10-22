package com.radustavila.nasaexplorer.data.spaceweather.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.SunImageRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.spaceweather.BuildConfig
import com.radustavila.nasaexplorer.data.spaceweather.remote.SdoService
import com.radustavila.nasaexplorer.data.spaceweather.sun.SdoImagery
import com.radustavila.nasaexplorer.data.spaceweather.sun.SdoSunImageRepository
import com.radustavila.nasaexplorer.data.spaceweather.sun.SunImageCacheEvictor
import com.radustavila.nasaexplorer.data.spaceweather.sun.SunImageFiles
import com.radustavila.nasaexplorer.data.spaceweather.sun.SunImageStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import java.net.URI
import java.time.Duration
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SdoApi

/** The DataStore holding which view of the Sun the user picked. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SunPreferencesStore

/** The Sun now (SDO) and at a past moment (Helioviewer). */
@Module
@InstallIn(SingletonComponent::class)
interface SunDataModule {

    @Binds
    fun bindSunImageRepository(impl: SdoSunImageRepository): SunImageRepository

    @Binds
    @IntoSet
    fun bindSunImageCacheEvictor(impl: SunImageCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindSunImageUsage(impl: SunImageStorage): StorageUsageSource

    @Binds
    @IntoSet
    fun bindSunImageCleaner(impl: SunImageStorage): CacheCleaner
}

@Module
@InstallIn(SingletonComponent::class)
object SunInfrastructureModule {

    @Provides
    @Singleton
    @SunPreferencesStore
    fun provideSunPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(SUN_PREFERENCES_STORE) },
    )

    /** Next to the image loader's cache: everything in it can be downloaded again. */
    @Provides
    @Named(SunImageFiles.SUN_IMAGE_DIRECTORY)
    fun provideSunImageDirectory(@ApplicationContext context: Context): File =
        File(context.cacheDir, SUN_IMAGE_DIRECTORY)

    @Provides
    fun provideSdoImagery(): SdoImagery = SdoImagery(
        sdoBaseUrl = BuildConfig.SDO_BASE_URL,
        helioviewerBaseUrl = BuildConfig.HELIOVIEWER_BASE_URL,
    )

    @Provides
    @Singleton
    @SdoApi
    fun provideSdoRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.SDO_BASE_URL)

    @Provides
    @Singleton
    fun provideSdoService(@SdoApi retrofit: Retrofit): SdoService = retrofit.create()

    /**
     * SDO publishes no quota. The card asks for one channel's picture at a time, about once every
     * 15 minutes per channel; switching through all six and pulling to refresh stays far below this.
     */
    @Provides
    @IntoSet
    fun provideSdoRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.SDO_BASE_URL).host,
        maxRequests = SDO_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    /**
     * Helioviewer publishes no quota, and each picture takes its servers a few seconds to render:
     * the app asks only when someone opens a flare's picture.
     */
    @Provides
    @IntoSet
    fun provideHelioviewerRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.HELIOVIEWER_BASE_URL).host,
        maxRequests = HELIOVIEWER_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val SUN_PREFERENCES_STORE = "sun_preferences"
    private const val SUN_IMAGE_DIRECTORY = "sun_images"
    private const val SDO_REQUESTS_PER_HOUR = 60
    private const val HELIOVIEWER_REQUESTS_PER_HOUR = 20
}
