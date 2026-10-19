package com.radustavila.nasaexplorer.data.mars.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.data.mars.BuildConfig
import com.radustavila.nasaexplorer.data.mars.cache.MarsPhotoCacheEvictor
import com.radustavila.nasaexplorer.data.mars.local.MarsDatabase
import com.radustavila.nasaexplorer.data.mars.local.MarsLocalDataSource
import com.radustavila.nasaexplorer.data.mars.local.MarsPhotoDao
import com.radustavila.nasaexplorer.data.mars.local.RoomMarsLocalDataSource
import com.radustavila.nasaexplorer.data.mars.remote.MarsRawImagesService
import com.radustavila.nasaexplorer.data.mars.remote.MarsRemoteDataSource
import com.radustavila.nasaexplorer.data.mars.remote.RetrofitMarsRemoteDataSource
import com.radustavila.nasaexplorer.data.mars.repository.MarsPhotoRepositoryImpl
import com.radustavila.nasaexplorer.data.mars.sync.WorkManagerMarsSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
interface MarsDataModule {

    @Binds
    fun bindMarsPhotoRepository(impl: MarsPhotoRepositoryImpl): MarsPhotoRepository

    @Binds
    fun bindMarsSyncScheduler(impl: WorkManagerMarsSyncScheduler): MarsSyncScheduler

    @Binds
    fun bindMarsLocalDataSource(impl: RoomMarsLocalDataSource): MarsLocalDataSource

    @Binds
    fun bindMarsRemoteDataSource(impl: RetrofitMarsRemoteDataSource): MarsRemoteDataSource

    @Binds
    @IntoSet
    fun bindMarsPhotoCacheEvictor(impl: MarsPhotoCacheEvictor): CacheEvictor
}

/**
 * No `HostRateBudget` for mars.nasa.gov: it publishes no quota, the API is called twice a day, and
 * the rover pictures themselves come from the same host, so a budget would throttle image loading.
 */
@Module
@InstallIn(SingletonComponent::class)
object MarsInfrastructureModule {

    @Provides
    @Singleton
    fun provideMarsDatabase(@ApplicationContext context: Context): MarsDatabase =
        Room.databaseBuilder(context, MarsDatabase::class.java, MarsDatabase.NAME)
            // A mirror of a public feed: rebuilding it costs one sync.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMarsPhotoDao(database: MarsDatabase): MarsPhotoDao = database.marsPhotoDao()

    /**
     * The shared client (rate limiting, logging) with room for Perseverance's feed, which answers in
     * 15-90 s. Only the background sync calls it.
     */
    @Provides
    @Singleton
    fun provideMarsRawImagesService(retrofitFactory: RetrofitFactory, client: OkHttpClient): MarsRawImagesService {
        val slowClient = client.newBuilder()
            .readTimeout(FEED_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(FEED_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        return retrofitFactory.create(baseUrl = BuildConfig.MARS_RAW_IMAGES_BASE_URL, callFactory = slowClient).create()
    }

    private const val FEED_TIMEOUT_SECONDS = 120L
}
