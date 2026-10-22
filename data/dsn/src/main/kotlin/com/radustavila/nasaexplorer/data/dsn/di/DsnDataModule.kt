package com.radustavila.nasaexplorer.data.dsn.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.DeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.dsn.BuildConfig
import com.radustavila.nasaexplorer.data.dsn.cache.DsnCacheCleaner
import com.radustavila.nasaexplorer.data.dsn.cache.DsnCacheEvictor
import com.radustavila.nasaexplorer.data.dsn.local.DsnDao
import com.radustavila.nasaexplorer.data.dsn.local.DsnDatabase
import com.radustavila.nasaexplorer.data.dsn.local.DsnLocalDataSource
import com.radustavila.nasaexplorer.data.dsn.local.RoomDsnLocalDataSource
import com.radustavila.nasaexplorer.data.dsn.remote.DsnNowService
import com.radustavila.nasaexplorer.data.dsn.remote.DsnRemoteDataSource
import com.radustavila.nasaexplorer.data.dsn.remote.HorizonsService
import com.radustavila.nasaexplorer.data.dsn.remote.RetrofitDsnRemoteDataSource
import com.radustavila.nasaexplorer.data.dsn.repository.DeepSpaceNetworkRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import java.time.Duration
import javax.inject.Singleton
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
internal interface DsnDataModule {

    @Binds
    fun bindDeepSpaceNetworkRepository(impl: DeepSpaceNetworkRepositoryImpl): DeepSpaceNetworkRepository

    @Binds
    fun bindDsnLocalDataSource(impl: RoomDsnLocalDataSource): DsnLocalDataSource

    @Binds
    fun bindDsnRemoteDataSource(impl: RetrofitDsnRemoteDataSource): DsnRemoteDataSource

    @Binds
    @IntoSet
    fun bindDsnCacheEvictor(impl: DsnCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindDsnCacheCleaner(impl: DsnCacheCleaner): CacheCleaner
}

@Module
@InstallIn(SingletonComponent::class)
internal object DsnInfrastructureModule {

    @Provides
    @Singleton
    fun provideDsnDatabase(@ApplicationContext context: Context): DsnDatabase =
        Room.databaseBuilder(context, DsnDatabase::class.java, DsnDatabase.NAME)
            // A disposable mirror of the feeds: rebuilding it costs one poll and one Horizons refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideDsnDao(database: DsnDatabase): DsnDao = database.dsnDao()

    @Provides
    @Singleton
    fun provideDsnNowService(retrofitFactory: RetrofitFactory): DsnNowService =
        retrofitFactory.create(BuildConfig.DSN_NOW_BASE_URL).create()

    @Provides
    @Singleton
    fun provideHorizonsService(retrofitFactory: RetrofitFactory): HorizonsService =
        retrofitFactory.create(BuildConfig.HORIZONS_BASE_URL).create()

    /**
     * DSN Now publishes no quota. The screen polls every 15 s while visible (240 calls an hour);
     * this leaves room for the Today card and the names list while stopping a runaway loop.
     */
    @Provides
    @IntoSet
    fun provideDsnNowRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.DSN_NOW_BASE_URL).host,
        maxRequests = DSN_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    /** Horizons publishes no quota either; the app needs seven calls a day. */
    @Provides
    @IntoSet
    fun provideHorizonsRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.HORIZONS_BASE_URL).host,
        maxRequests = HORIZONS_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val DSN_REQUESTS_PER_HOUR = 400
    private const val HORIZONS_REQUESTS_PER_HOUR = 30
}
