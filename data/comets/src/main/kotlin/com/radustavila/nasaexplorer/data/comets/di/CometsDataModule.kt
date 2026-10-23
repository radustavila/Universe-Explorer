package com.radustavila.nasaexplorer.data.comets.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.radustavila.nasaexplorer.core.astro.AstronomyEngineCometSkyCalculator
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.ApplicationScope
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.repository.CometDebugSettingsRepository
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.sky.CometSkyCalculator
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.comets.BuildConfig
import com.radustavila.nasaexplorer.data.comets.cache.CometsCacheCleaner
import com.radustavila.nasaexplorer.data.comets.cache.CometsCacheEvictor
import com.radustavila.nasaexplorer.data.comets.local.CometsDao
import com.radustavila.nasaexplorer.data.comets.local.CometsDatabase
import com.radustavila.nasaexplorer.data.comets.local.CometsLocalDataSource
import com.radustavila.nasaexplorer.data.comets.local.RoomCometsLocalDataSource
import com.radustavila.nasaexplorer.data.comets.preferences.CometDebugSettingsDataSource
import com.radustavila.nasaexplorer.data.comets.remote.CometsRemoteDataSource
import com.radustavila.nasaexplorer.data.comets.remote.HorizonsCometService
import com.radustavila.nasaexplorer.data.comets.remote.RetrofitCometsRemoteDataSource
import com.radustavila.nasaexplorer.data.comets.remote.SbdbQueryService
import com.radustavila.nasaexplorer.data.comets.repository.CometRepositoryImpl
import com.radustavila.nasaexplorer.data.comets.search.CometSearchableSource
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

/** The DataStore holding the debug screen's comets switch. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class CometDebugStore

@Module
@InstallIn(SingletonComponent::class)
internal interface CometsDataModule {

    @Binds
    fun bindCometRepository(impl: CometRepositoryImpl): CometRepository

    @Binds
    fun bindCometSkyCalculator(impl: AstronomyEngineCometSkyCalculator): CometSkyCalculator

    @Binds
    fun bindCometDebugSettingsRepository(impl: CometDebugSettingsDataSource): CometDebugSettingsRepository

    @Binds
    fun bindCometsLocalDataSource(impl: RoomCometsLocalDataSource): CometsLocalDataSource

    @Binds
    fun bindCometsRemoteDataSource(impl: RetrofitCometsRemoteDataSource): CometsRemoteDataSource

    @Binds
    @IntoSet
    fun bindCometsCacheEvictor(impl: CometsCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindCometsCacheCleaner(impl: CometsCacheCleaner): CacheCleaner

    @Binds
    @IntoSet
    fun bindCometSearchableSource(impl: CometSearchableSource): SearchableSource
}

@Module
@InstallIn(SingletonComponent::class)
internal object CometsInfrastructureModule {

    @Provides
    @Singleton
    fun provideCometsDatabase(@ApplicationContext context: Context): CometsDatabase =
        Room.databaseBuilder(context, CometsDatabase::class.java, CometsDatabase.NAME)
            // A disposable mirror of JPL: rebuilding it costs one daily refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    @CometDebugStore
    fun provideCometDebugDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(COMET_DEBUG_STORE) },
    )

    @Provides
    fun provideCometsDao(database: CometsDatabase): CometsDao = database.cometsDao()

    @Provides
    @Singleton
    fun provideSbdbQueryService(retrofitFactory: RetrofitFactory): SbdbQueryService =
        retrofitFactory.create(BuildConfig.JPL_SSD_BASE_URL).create()

    @Provides
    @Singleton
    fun provideHorizonsCometService(retrofitFactory: RetrofitFactory): HorizonsCometService =
        retrofitFactory.create(BuildConfig.HORIZONS_BASE_URL).create()

    /**
     * The same hosts as the asteroid lists (F6) and the DSN distances (F13), with the same budgets:
     * the strictest budget per host wins, so these only matter if those modules go. A daily comets
     * refresh is one SBDB query and up to ten Horizons requests.
     */
    @Provides
    @IntoSet
    fun provideJplSsdRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.JPL_SSD_BASE_URL).host,
        maxRequests = JPL_SSD_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    @Provides
    @IntoSet
    fun provideHorizonsRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.HORIZONS_BASE_URL).host,
        maxRequests = HORIZONS_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val COMET_DEBUG_STORE = "comet_debug"
    private const val JPL_SSD_REQUESTS_PER_HOUR = 60
    private const val HORIZONS_REQUESTS_PER_HOUR = 30
}
