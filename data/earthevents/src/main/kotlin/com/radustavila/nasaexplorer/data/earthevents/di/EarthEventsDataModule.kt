package com.radustavila.nasaexplorer.data.earthevents.di

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
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.earthevents.BuildConfig
import com.radustavila.nasaexplorer.data.earthevents.alerts.DataStoreEarthquakeAlertRepository
import com.radustavila.nasaexplorer.data.earthevents.cache.EarthEventsCacheEvictor
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventDao
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakeDao
import com.radustavila.nasaexplorer.data.earthevents.local.EarthquakesLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthquakesLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetService
import com.radustavila.nasaexplorer.data.earthevents.remote.RetrofitEonetRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.RetrofitUsgsRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.UsgsRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.UsgsService
import com.radustavila.nasaexplorer.data.earthevents.repository.EarthEventRepositoryImpl
import com.radustavila.nasaexplorer.data.earthevents.repository.EarthquakeRepositoryImpl
import com.radustavila.nasaexplorer.data.earthevents.search.EarthEventSearchableSource
import com.radustavila.nasaexplorer.data.earthevents.search.EarthquakeSearchableSource
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
annotation class EonetApi

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UsgsApi

/** The DataStore holding the user's strong-quake alert choice. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EarthquakeAlertStore

@Module
@InstallIn(SingletonComponent::class)
interface EarthEventsDataModule {

    @Binds
    fun bindEarthEventRepository(impl: EarthEventRepositoryImpl): EarthEventRepository

    @Binds
    fun bindEarthEventsLocalDataSource(impl: RoomEarthEventsLocalDataSource): EarthEventsLocalDataSource

    @Binds
    fun bindEonetRemoteDataSource(impl: RetrofitEonetRemoteDataSource): EonetRemoteDataSource

    @Binds
    @IntoSet
    fun bindEarthEventsCacheEvictor(impl: EarthEventsCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindEarthEventSearchableSource(impl: EarthEventSearchableSource): SearchableSource

    @Binds
    fun bindEarthquakeRepository(impl: EarthquakeRepositoryImpl): EarthquakeRepository

    @Binds
    fun bindEarthquakesLocalDataSource(impl: RoomEarthquakesLocalDataSource): EarthquakesLocalDataSource

    @Binds
    fun bindUsgsRemoteDataSource(impl: RetrofitUsgsRemoteDataSource): UsgsRemoteDataSource

    @Binds
    fun bindEarthquakeAlertRepository(impl: DataStoreEarthquakeAlertRepository): EarthquakeAlertRepository

    @Binds
    @IntoSet
    fun bindEarthquakeSearchableSource(impl: EarthquakeSearchableSource): SearchableSource
}

@Module
@InstallIn(SingletonComponent::class)
object EarthEventsInfrastructureModule {

    @Provides
    @Singleton
    fun provideEarthEventsDatabase(@ApplicationContext context: Context): EarthEventsDatabase =
        Room.databaseBuilder(context, EarthEventsDatabase::class.java, EarthEventsDatabase.NAME)
            // A mirror of a public feed without user data: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideEarthEventDao(database: EarthEventsDatabase): EarthEventDao = database.earthEventDao()

    @Provides
    fun provideEarthquakeDao(database: EarthEventsDatabase): EarthquakeDao = database.earthquakeDao()

    @Provides
    @Singleton
    @EonetApi
    fun provideEonetRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.EONET_BASE_URL)

    @Provides
    @Singleton
    fun provideEonetService(@EonetApi retrofit: Retrofit): EonetService = retrofit.create()

    /**
     * EONET answers with `X-RateLimit-Limit: 60` without saying per what; an hour is the safe
     * reading. The app needs one call per refresh plus one per category the first time imagery is
     * shown.
     */
    @Provides
    @IntoSet
    fun provideEonetRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.EONET_BASE_URL).host,
        maxRequests = 60,
        window = Duration.ofHours(1),
    )

    @Provides
    @Singleton
    @UsgsApi
    fun provideUsgsRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.USGS_BASE_URL)

    @Provides
    @Singleton
    fun provideUsgsService(@UsgsApi retrofit: Retrofit): UsgsService = retrofit.create()

    /**
     * USGS publishes no limit. Two feeds per refresh at most every 15 minutes is 8 an hour; the
     * budget leaves room for the refresh button and stops a bug from hammering the server.
     */
    @Provides
    @IntoSet
    fun provideUsgsRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.USGS_BASE_URL).host,
        maxRequests = USGS_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    @Provides
    @Singleton
    @EarthquakeAlertStore
    fun provideEarthquakeAlertDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        @Dispatcher(AppDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = applicationScope + ioDispatcher,
        produceFile = { context.preferencesDataStoreFile(EARTHQUAKE_ALERTS_STORE) },
    )

    private const val USGS_REQUESTS_PER_HOUR = 30
    private const val EARTHQUAKE_ALERTS_STORE = "earthquake_alerts"
}
