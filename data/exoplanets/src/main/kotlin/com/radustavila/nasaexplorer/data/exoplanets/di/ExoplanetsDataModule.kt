package com.radustavila.nasaexplorer.data.exoplanets.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.exoplanets.BuildConfig
import com.radustavila.nasaexplorer.data.exoplanets.cache.ExoplanetCacheEvictor
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetDao
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetLocalDataSource
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetsDatabase
import com.radustavila.nasaexplorer.data.exoplanets.local.RoomExoplanetLocalDataSource
import com.radustavila.nasaexplorer.data.exoplanets.remote.ExoplanetArchiveService
import com.radustavila.nasaexplorer.data.exoplanets.remote.ExoplanetRemoteDataSource
import com.radustavila.nasaexplorer.data.exoplanets.remote.RetrofitExoplanetRemoteDataSource
import com.radustavila.nasaexplorer.data.exoplanets.repository.ExoplanetRepositoryImpl
import com.radustavila.nasaexplorer.data.exoplanets.sync.WorkManagerExoplanetSyncScheduler
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ExoplanetArchiveApi

@Module
@InstallIn(SingletonComponent::class)
interface ExoplanetsDataModule {

    @Binds
    fun bindExoplanetRepository(impl: ExoplanetRepositoryImpl): ExoplanetRepository

    @Binds
    fun bindExoplanetSyncScheduler(impl: WorkManagerExoplanetSyncScheduler): ExoplanetSyncScheduler

    @Binds
    fun bindExoplanetLocalDataSource(impl: RoomExoplanetLocalDataSource): ExoplanetLocalDataSource

    @Binds
    fun bindExoplanetRemoteDataSource(impl: RetrofitExoplanetRemoteDataSource): ExoplanetRemoteDataSource

    @Binds
    @IntoSet
    fun bindExoplanetCacheEvictor(impl: ExoplanetCacheEvictor): CacheEvictor
}

@Module
@InstallIn(SingletonComponent::class)
object ExoplanetsInfrastructureModule {

    @Provides
    @Singleton
    fun provideExoplanetsDatabase(@ApplicationContext context: Context): ExoplanetsDatabase =
        Room.databaseBuilder(context, ExoplanetsDatabase::class.java, ExoplanetsDatabase.NAME)
            // A disposable mirror of the archive: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideExoplanetDao(database: ExoplanetsDatabase): ExoplanetDao = database.exoplanetDao()

    /**
     * The shared client gives up after 20 s without data. A TAP query is evaluated before the
     * archive sends its first byte, and the catalogue query has taken well over that when the
     * archive is busy, so this client waits longer. `newBuilder()` keeps the shared connection
     * pool and interceptors, including the rate limiter.
     */
    @Provides
    @Singleton
    @ExoplanetArchiveApi
    fun provideExoplanetArchiveRetrofit(retrofitFactory: RetrofitFactory, sharedClient: OkHttpClient): Retrofit =
        retrofitFactory.create(
            baseUrl = BuildConfig.EXOPLANET_ARCHIVE_BASE_URL,
            callFactory = sharedClient.newBuilder()
                .readTimeout(ARCHIVE_READ_TIMEOUT)
                .callTimeout(ARCHIVE_CALL_TIMEOUT)
                .build(),
        )

    @Provides
    @Singleton
    fun provideExoplanetArchiveService(@ExoplanetArchiveApi retrofit: Retrofit): ExoplanetArchiveService =
        retrofit.create()

    /**
     * The archive publishes no quota. The app needs two calls a week; this keeps a misbehaving
     * build (or a pull-to-refresh loop) polite.
     */
    @Provides
    @IntoSet
    fun provideExoplanetArchiveRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.EXOPLANET_ARCHIVE_BASE_URL).host,
        maxRequests = ARCHIVE_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val ARCHIVE_REQUESTS_PER_HOUR = 30
    private val ARCHIVE_READ_TIMEOUT: Duration = Duration.ofSeconds(60)
    private val ARCHIVE_CALL_TIMEOUT: Duration = Duration.ofSeconds(90)
}
