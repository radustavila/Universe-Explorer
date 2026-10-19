package com.radustavila.nasaexplorer.data.nasaimages.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.nasaimages.BuildConfig
import com.radustavila.nasaexplorer.data.nasaimages.cache.NasaMediaCacheEvictor
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesDatabase
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesLocalDataSource
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaMediaDao
import com.radustavila.nasaexplorer.data.nasaimages.local.RoomNasaImagesLocalDataSource
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesRemoteDataSource
import com.radustavila.nasaexplorer.data.nasaimages.remote.NasaImagesService
import com.radustavila.nasaexplorer.data.nasaimages.remote.RetrofitNasaImagesRemoteDataSource
import com.radustavila.nasaexplorer.data.nasaimages.repository.NasaMediaRepositoryImpl
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
annotation class NasaImagesApi

@Module
@InstallIn(SingletonComponent::class)
interface NasaImagesDataModule {

    @Binds
    fun bindNasaMediaRepository(impl: NasaMediaRepositoryImpl): NasaMediaRepository

    @Binds
    fun bindNasaImagesLocalDataSource(impl: RoomNasaImagesLocalDataSource): NasaImagesLocalDataSource

    @Binds
    fun bindNasaImagesRemoteDataSource(impl: RetrofitNasaImagesRemoteDataSource): NasaImagesRemoteDataSource

    @Binds
    @IntoSet
    fun bindNasaMediaCacheEvictor(impl: NasaMediaCacheEvictor): CacheEvictor
}

@Module
@InstallIn(SingletonComponent::class)
object NasaImagesInfrastructureModule {

    @Provides
    @Singleton
    fun provideNasaImagesDatabase(@ApplicationContext context: Context): NasaImagesDatabase =
        Room.databaseBuilder(context, NasaImagesDatabase::class.java, NasaImagesDatabase.NAME)
            // A mirror of a public catalogue: rebuilding it costs one rail refresh, and saved items
            // can be fetched again by id.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideNasaMediaDao(database: NasaImagesDatabase): NasaMediaDao = database.nasaMediaDao()

    @Provides
    @Singleton
    @NasaImagesApi
    fun provideNasaImagesRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.NASA_IMAGES_BASE_URL)

    @Provides
    @Singleton
    fun provideNasaImagesService(@NasaImagesApi retrofit: Retrofit): NasaImagesService = retrofit.create()

    /**
     * The library publishes no quota. A refresh is six calls a day and search waits for the user
     * to stop typing, so this only stops a runaway loop from hammering a free public API.
     */
    @Provides
    @IntoSet
    fun provideNasaImagesRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.NASA_IMAGES_BASE_URL).host,
        maxRequests = 300,
        window = Duration.ofHours(1),
    )
}
