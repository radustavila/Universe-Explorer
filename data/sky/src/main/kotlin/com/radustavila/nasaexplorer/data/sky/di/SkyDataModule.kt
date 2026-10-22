package com.radustavila.nasaexplorer.data.sky.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.radustavila.nasaexplorer.core.astro.AstronomyEngineSkyCalculator
import com.radustavila.nasaexplorer.core.astro.Sgp4PassPredictor
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.sky.IssPassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCalculator
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.sky.BuildConfig
import com.radustavila.nasaexplorer.data.sky.cache.OrbitalElementsCacheEvictor
import com.radustavila.nasaexplorer.data.sky.local.OrbitalElementsDao
import com.radustavila.nasaexplorer.data.sky.local.SkyDatabase
import com.radustavila.nasaexplorer.data.sky.preferences.SkyPreferencesDataSource
import com.radustavila.nasaexplorer.data.sky.reminder.WorkManagerIssPassReminderScheduler
import com.radustavila.nasaexplorer.data.sky.reminder.WorkManagerSatellitePassReminderScheduler
import com.radustavila.nasaexplorer.data.sky.remote.CelesTrakService
import com.radustavila.nasaexplorer.data.sky.remote.RetrofitSkyRemoteDataSource
import com.radustavila.nasaexplorer.data.sky.remote.SkyRemoteDataSource
import com.radustavila.nasaexplorer.data.sky.remote.WhereTheIssService
import com.radustavila.nasaexplorer.data.sky.repository.SatelliteRepositoryImpl
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
import retrofit2.create

/** The DataStore holding Sky tonight's choices (ISS reminders). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SkyPreferencesStore

/** What talks to the network or the device's storage; the end-to-end test swaps this module. */
@Module
@InstallIn(SingletonComponent::class)
interface SkyDataModule {

    @Binds
    fun bindSatelliteRepository(impl: SatelliteRepositoryImpl): SatelliteRepository

    @Binds
    fun bindSkyRemoteDataSource(impl: RetrofitSkyRemoteDataSource): SkyRemoteDataSource

    @Binds
    fun bindSkyPreferencesRepository(impl: SkyPreferencesDataSource): SkyPreferencesRepository

    @Binds
    fun bindIssPassReminderScheduler(impl: WorkManagerIssPassReminderScheduler): IssPassReminderScheduler

    @Binds
    fun bindSatellitePassReminderScheduler(
        impl: WorkManagerSatellitePassReminderScheduler,
    ): SatellitePassReminderScheduler

    @Binds
    @IntoSet
    fun bindOrbitalElementsCacheEvictor(impl: OrbitalElementsCacheEvictor): CacheEvictor
}

/** The on-device astronomy: pure computation, the same in tests and production. */
@Module
@InstallIn(SingletonComponent::class)
interface AstronomyModule {

    @Binds
    fun bindSkyCalculator(impl: AstronomyEngineSkyCalculator): SkyCalculator

    @Binds
    fun bindSatellitePassPredictor(impl: Sgp4PassPredictor): SatellitePassPredictor
}

@Module
@InstallIn(SingletonComponent::class)
object SkyInfrastructureModule {

    @Provides
    @Singleton
    fun provideSkyDatabase(@ApplicationContext context: Context): SkyDatabase =
        Room.databaseBuilder(context, SkyDatabase::class.java, SkyDatabase.NAME)
            // Only public orbital elements: rebuilding costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideOrbitalElementsDao(database: SkyDatabase): OrbitalElementsDao = database.orbitalElementsDao()

    @Provides
    @Singleton
    fun provideCelesTrakService(retrofitFactory: RetrofitFactory): CelesTrakService =
        retrofitFactory.create(baseUrl = BuildConfig.CELESTRAK_BASE_URL).create()

    @Provides
    @Singleton
    fun provideWhereTheIssService(retrofitFactory: RetrofitFactory): WhereTheIssService =
        retrofitFactory.create(baseUrl = BuildConfig.WHERE_THE_ISS_BASE_URL).create()

    /**
     * CelesTrak publishes no number, only "do not download the same data more than once every
     * two hours"; the ISS is refreshed twice a day and every other set once (up to five requests
     * when all are due at once), and this stops a loop from getting the IP blocked.
     */
    @Provides
    @IntoSet
    fun provideCelesTrakRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.CELESTRAK_BASE_URL).host,
        maxRequests = 10,
        window = Duration.ofHours(1),
    )

    /** wheretheiss.at answers with `X-Rate-Limit-Limit: 350` per 5 minutes; polling uses 60. */
    @Provides
    @IntoSet
    fun provideWhereTheIssRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.WHERE_THE_ISS_BASE_URL).host,
        maxRequests = 300,
        window = Duration.ofMinutes(5),
    )

    @Provides
    @Singleton
    @SkyPreferencesStore
    fun provideSkyPreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(SKY_PREFERENCES_NAME) },
    )

    private const val SKY_PREFERENCES_NAME = "sky_preferences"
}
