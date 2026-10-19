package com.radustavila.nasaexplorer.data.earthevents.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.earthevents.BuildConfig
import com.radustavila.nasaexplorer.data.earthevents.cache.EarthEventsCacheEvictor
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventDao
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetService
import com.radustavila.nasaexplorer.data.earthevents.remote.RetrofitEonetRemoteDataSource
import com.radustavila.nasaexplorer.data.earthevents.repository.EarthEventRepositoryImpl
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
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EonetApi

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
}
