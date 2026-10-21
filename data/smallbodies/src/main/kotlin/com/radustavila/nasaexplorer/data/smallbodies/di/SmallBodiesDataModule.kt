package com.radustavila.nasaexplorer.data.smallbodies.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.sync.SmallBodiesSyncScheduler
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.smallbodies.BuildConfig
import com.radustavila.nasaexplorer.data.smallbodies.cache.SmallBodiesCacheEvictor
import com.radustavila.nasaexplorer.data.smallbodies.local.RoomSmallBodiesLocalDataSource
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDao
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDatabase
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesLocalDataSource
import com.radustavila.nasaexplorer.data.smallbodies.remote.JplSsdService
import com.radustavila.nasaexplorer.data.smallbodies.remote.RetrofitSmallBodiesRemoteDataSource
import com.radustavila.nasaexplorer.data.smallbodies.remote.SmallBodiesRemoteDataSource
import com.radustavila.nasaexplorer.data.smallbodies.repository.SmallBodiesRepositoryImpl
import com.radustavila.nasaexplorer.data.smallbodies.search.SmallBodySearchableSource
import com.radustavila.nasaexplorer.data.smallbodies.sync.WorkManagerSmallBodiesSyncScheduler
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
annotation class JplSsdApi

@Module
@InstallIn(SingletonComponent::class)
interface SmallBodiesDataModule {

    @Binds
    fun bindSmallBodiesRepository(impl: SmallBodiesRepositoryImpl): SmallBodiesRepository

    @Binds
    fun bindSmallBodiesSyncScheduler(impl: WorkManagerSmallBodiesSyncScheduler): SmallBodiesSyncScheduler

    @Binds
    fun bindSmallBodiesLocalDataSource(impl: RoomSmallBodiesLocalDataSource): SmallBodiesLocalDataSource

    @Binds
    fun bindSmallBodiesRemoteDataSource(impl: RetrofitSmallBodiesRemoteDataSource): SmallBodiesRemoteDataSource

    @Binds
    @IntoSet
    fun bindSmallBodiesCacheEvictor(impl: SmallBodiesCacheEvictor): CacheEvictor

    @Binds
    @IntoSet
    fun bindSmallBodySearchableSource(impl: SmallBodySearchableSource): SearchableSource
}

@Module
@InstallIn(SingletonComponent::class)
object SmallBodiesInfrastructureModule {

    @Provides
    @Singleton
    fun provideSmallBodiesDatabase(@ApplicationContext context: Context): SmallBodiesDatabase =
        Room.databaseBuilder(context, SmallBodiesDatabase::class.java, SmallBodiesDatabase.NAME)
            // A disposable mirror of JPL: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideSmallBodiesDao(database: SmallBodiesDatabase): SmallBodiesDao = database.smallBodiesDao()

    @Provides
    @Singleton
    @JplSsdApi
    fun provideJplSsdRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.JPL_SSD_BASE_URL)

    @Provides
    @Singleton
    fun provideJplSsdService(@JplSsdApi retrofit: Retrofit): JplSsdService = retrofit.create()

    /**
     * JPL publishes no quota. The daily refresh makes three calls and each details screen one; this
     * keeps a tapping spree or a refresh loop polite.
     */
    @Provides
    @IntoSet
    fun provideJplSsdRateBudget(): HostRateBudget = HostRateBudget(
        host = URI(BuildConfig.JPL_SSD_BASE_URL).host,
        maxRequests = JPL_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val JPL_REQUESTS_PER_HOUR = 60
}
