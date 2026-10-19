package com.radustavila.nasaexplorer.data.spaceweather.di

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
import com.radustavila.nasaexplorer.core.domain.repository.AuroraAlertRepository
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.spaceweather.BuildConfig
import com.radustavila.nasaexplorer.data.spaceweather.alerts.DataStoreAuroraAlertRepository
import com.radustavila.nasaexplorer.data.spaceweather.cache.SpaceWeatherCacheEvictor
import com.radustavila.nasaexplorer.data.spaceweather.local.RoomSpaceWeatherLocalDataSource
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDao
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDatabase
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherLocalDataSource
import com.radustavila.nasaexplorer.data.spaceweather.remote.DonkiService
import com.radustavila.nasaexplorer.data.spaceweather.remote.RetrofitSpaceWeatherRemoteDataSource
import com.radustavila.nasaexplorer.data.spaceweather.remote.SpaceWeatherRemoteDataSource
import com.radustavila.nasaexplorer.data.spaceweather.remote.SwpcService
import com.radustavila.nasaexplorer.data.spaceweather.repository.SpaceWeatherRepositoryImpl
import com.radustavila.nasaexplorer.data.spaceweather.sync.WorkManagerSpaceWeatherSyncScheduler
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
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SwpcApi

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DonkiApi

/** The DataStore holding the user's aurora alert choices. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AuroraAlertStore

@Module
@InstallIn(SingletonComponent::class)
interface SpaceWeatherDataModule {

    @Binds
    fun bindSpaceWeatherRepository(impl: SpaceWeatherRepositoryImpl): SpaceWeatherRepository

    @Binds
    fun bindSpaceWeatherSyncScheduler(impl: WorkManagerSpaceWeatherSyncScheduler): SpaceWeatherSyncScheduler

    @Binds
    fun bindAuroraAlertRepository(impl: DataStoreAuroraAlertRepository): AuroraAlertRepository

    @Binds
    fun bindSpaceWeatherLocalDataSource(impl: RoomSpaceWeatherLocalDataSource): SpaceWeatherLocalDataSource

    @Binds
    fun bindSpaceWeatherRemoteDataSource(impl: RetrofitSpaceWeatherRemoteDataSource): SpaceWeatherRemoteDataSource

    @Binds
    @IntoSet
    fun bindSpaceWeatherCacheEvictor(impl: SpaceWeatherCacheEvictor): CacheEvictor
}

@Module
@InstallIn(SingletonComponent::class)
object SpaceWeatherInfrastructureModule {

    @Provides
    @Singleton
    fun provideSpaceWeatherDatabase(@ApplicationContext context: Context): SpaceWeatherDatabase =
        Room.databaseBuilder(context, SpaceWeatherDatabase::class.java, SpaceWeatherDatabase.NAME)
            // A disposable mirror of NOAA and DONKI: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSpaceWeatherDao(database: SpaceWeatherDatabase): SpaceWeatherDao = database.spaceWeatherDao()

    @Provides
    @Singleton
    @AuroraAlertStore
    fun provideAuroraAlertDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(AURORA_ALERTS_STORE) },
    )

    @Provides
    @Singleton
    @SwpcApi
    fun provideSwpcRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.SWPC_BASE_URL)

    @Provides
    @Singleton
    fun provideSwpcService(@SwpcApi retrofit: Retrofit): SwpcService = retrofit.create()

    @Provides
    @Singleton
    @DonkiApi
    fun provideDonkiRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.DONKI_BASE_URL)

    @Provides
    @Singleton
    fun provideDonkiService(@DonkiApi retrofit: Retrofit): DonkiService = retrofit.create()

    /**
     * SWPC publishes no quota. The app needs about six calls an hour (two every 30 minutes, the
     * aurora grid while the screen is open); this keeps a refresh loop polite.
     */
    @Provides
    @IntoSet
    fun provideSwpcRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.SWPC_BASE_URL).host,
        maxRequests = SWPC_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    /** DONKI publishes no quota either (it sends an `X-Rate-Limit-Remaining` of 120); two calls every few hours. */
    @Provides
    @IntoSet
    fun provideDonkiRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.DONKI_BASE_URL).host,
        maxRequests = DONKI_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val AURORA_ALERTS_STORE = "aurora_alerts"
    private const val SWPC_REQUESTS_PER_HOUR = 60
    private const val DONKI_REQUESTS_PER_HOUR = 30
}
