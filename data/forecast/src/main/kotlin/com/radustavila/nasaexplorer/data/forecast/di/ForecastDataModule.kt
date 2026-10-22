package com.radustavila.nasaexplorer.data.forecast.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.stargazing.CloudForecastRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertRepository
import com.radustavila.nasaexplorer.core.domain.stargazing.StargazingAlertScheduler
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.forecast.BuildConfig
import com.radustavila.nasaexplorer.data.forecast.alerts.DataStoreStargazingAlertRepository
import com.radustavila.nasaexplorer.data.forecast.alerts.WorkManagerStargazingAlertScheduler
import com.radustavila.nasaexplorer.data.forecast.cache.ForecastCacheCleaner
import com.radustavila.nasaexplorer.data.forecast.cache.ForecastCacheEvictor
import com.radustavila.nasaexplorer.data.forecast.local.ForecastDao
import com.radustavila.nasaexplorer.data.forecast.local.ForecastDatabase
import com.radustavila.nasaexplorer.data.forecast.local.ForecastLocalDataSource
import com.radustavila.nasaexplorer.data.forecast.local.RoomForecastLocalDataSource
import com.radustavila.nasaexplorer.data.forecast.remote.ForecastRemoteDataSource
import com.radustavila.nasaexplorer.data.forecast.remote.MetNorwayRemoteDataSource
import com.radustavila.nasaexplorer.data.forecast.remote.MetNorwayService
import com.radustavila.nasaexplorer.data.forecast.remote.MetNorwayUserAgentInterceptor
import com.radustavila.nasaexplorer.data.forecast.repository.MetNorwayCloudForecastRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import okhttp3.Call
import okhttp3.OkHttpClient
import retrofit2.create

/** The DataStore holding the user's clear-night alert choices. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StargazingAlertStore

@Module
@InstallIn(SingletonComponent::class)
internal interface ForecastDataModule {

    @Binds
    fun bindCloudForecastRepository(impl: MetNorwayCloudForecastRepository): CloudForecastRepository

    @Binds
    fun bindForecastLocalDataSource(impl: RoomForecastLocalDataSource): ForecastLocalDataSource

    @Binds
    fun bindForecastRemoteDataSource(impl: MetNorwayRemoteDataSource): ForecastRemoteDataSource

    @Binds
    fun bindStargazingAlertRepository(impl: DataStoreStargazingAlertRepository): StargazingAlertRepository

    @Binds
    fun bindStargazingAlertScheduler(impl: WorkManagerStargazingAlertScheduler): StargazingAlertScheduler

    @Binds
    @IntoSet
    fun bindForecastCacheEvictor(impl: ForecastCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindForecastCacheCleaner(impl: ForecastCacheCleaner): CacheCleaner
}

@Module
@InstallIn(SingletonComponent::class)
internal object ForecastInfrastructureModule {

    @Provides
    @Singleton
    fun provideForecastDatabase(@ApplicationContext context: Context): ForecastDatabase =
        Room.databaseBuilder(context, ForecastDatabase::class.java, ForecastDatabase.NAME)
            // A disposable mirror of MET Norway: rebuilding it costs one download.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideForecastDao(database: ForecastDatabase): ForecastDao = database.forecastDao()

    /**
     * The shared client plus MET Norway's User-Agent, built on the first request (off the main
     * thread, like the shared client itself).
     */
    @Provides
    @Singleton
    fun provideMetNorwayService(
        @ApplicationContext context: Context,
        retrofitFactory: RetrofitFactory,
        client: dagger.Lazy<OkHttpClient>,
    ): MetNorwayService {
        val metClient by lazy {
            client.get().newBuilder()
                .addInterceptor(MetNorwayUserAgentInterceptor { userAgent(context) })
                .build()
        }
        val callFactory = Call.Factory { request -> metClient.newCall(request) }
        return retrofitFactory.create(BuildConfig.MET_NORWAY_BASE_URL, callFactory).create()
    }

    /** MET Norway allows 20 requests a second per app; the app needs a few an hour. */
    @Provides
    @IntoSet
    fun provideMetNorwayRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.MET_NORWAY_BASE_URL).host,
        maxRequests = MET_NORWAY_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    @Provides
    @Singleton
    @StargazingAlertStore
    fun provideStargazingAlertDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(STARGAZING_ALERTS_STORE) },
    )

    /** "NasaExplorer/1.4.0 (+https://github.com/…)": the app, its version and where to reach its maker. */
    private fun userAgent(context: Context): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "dev"
        return "NasaExplorer/$version (+${BuildConfig.MET_NORWAY_CONTACT})"
    }

    private const val MET_NORWAY_REQUESTS_PER_HOUR = 60
    private const val STARGAZING_ALERTS_STORE = "stargazing_alerts"
}
