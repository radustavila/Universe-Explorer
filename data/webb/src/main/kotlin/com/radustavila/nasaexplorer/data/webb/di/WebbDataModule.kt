package com.radustavila.nasaexplorer.data.webb.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.domain.repository.WebbScheduleRepository
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.webb.BuildConfig
import com.radustavila.nasaexplorer.data.webb.cache.WebbCacheCleaner
import com.radustavila.nasaexplorer.data.webb.local.RoomWebbLocalDataSource
import com.radustavila.nasaexplorer.data.webb.local.WebbDao
import com.radustavila.nasaexplorer.data.webb.local.WebbDatabase
import com.radustavila.nasaexplorer.data.webb.local.WebbLocalDataSource
import com.radustavila.nasaexplorer.data.webb.remote.RetrofitWebbRemoteDataSource
import com.radustavila.nasaexplorer.data.webb.remote.StsciScheduleService
import com.radustavila.nasaexplorer.data.webb.remote.WebbRemoteDataSource
import com.radustavila.nasaexplorer.data.webb.remote.WebbScheduleIndexUrl
import com.radustavila.nasaexplorer.data.webb.repository.WebbScheduleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
internal interface WebbDataModule {

    @Binds
    fun bindWebbScheduleRepository(impl: WebbScheduleRepositoryImpl): WebbScheduleRepository

    @Binds
    fun bindWebbLocalDataSource(impl: RoomWebbLocalDataSource): WebbLocalDataSource

    @Binds
    fun bindWebbRemoteDataSource(impl: RetrofitWebbRemoteDataSource): WebbRemoteDataSource

    @Binds
    @IntoSet
    fun bindWebbCacheCleaner(impl: WebbCacheCleaner): CacheCleaner
}

@Module
@InstallIn(SingletonComponent::class)
internal object WebbInfrastructureModule {

    @Provides
    @Singleton
    fun provideWebbDatabase(@ApplicationContext context: Context): WebbDatabase =
        Room.databaseBuilder(context, WebbDatabase::class.java, WebbDatabase.NAME)
            // A disposable copy of STScI's week: rebuilding it costs one daily refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWebbDao(database: WebbDatabase): WebbDao = database.webbDao()

    @Provides
    fun provideWebbScheduleIndexUrl(): WebbScheduleIndexUrl =
        WebbScheduleIndexUrl(BuildConfig.JWST_SCHEDULES_URL.toHttpUrl())

    /** The URLs are absolute (`@Url`), so the base URL only satisfies Retrofit. */
    @Provides
    @Singleton
    fun provideStsciScheduleService(retrofitFactory: RetrofitFactory): StsciScheduleService =
        retrofitFactory.create(
            BuildConfig.JWST_SCHEDULES_URL.toHttpUrl().newBuilder().encodedPath("/").build().toString(),
        ).create()

    /** A daily refresh is two or three requests; STScI asks for once a day, this only stops runaway loops. */
    @Provides
    @IntoSet
    fun provideStsciRateBudget(): HostRateBudget = HostRateBudget(
        host = BuildConfig.JWST_SCHEDULES_URL.toHttpUrl().host,
        maxRequests = STSCI_REQUESTS_PER_HOUR,
        window = Duration.ofHours(1),
    )

    private const val STSCI_REQUESTS_PER_HOUR = 12
}
