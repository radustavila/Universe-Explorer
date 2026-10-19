package com.radustavila.nasaexplorer.data.launches.di

import android.content.Context
import androidx.room.Room
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.data.launches.BuildConfig
import com.radustavila.nasaexplorer.data.launches.local.LaunchDao
import com.radustavila.nasaexplorer.data.launches.local.LaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.launches.local.RoomLaunchLocalDataSource
import com.radustavila.nasaexplorer.data.launches.remote.LaunchLibraryService
import com.radustavila.nasaexplorer.data.launches.remote.LaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.remote.RetrofitLaunchRemoteDataSource
import com.radustavila.nasaexplorer.data.launches.repository.LaunchRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.net.URI
import javax.inject.Qualifier
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LaunchLibraryApi

@Module
@InstallIn(SingletonComponent::class)
interface LaunchesDataModule {

    @Binds
    fun bindLaunchRepository(impl: LaunchRepositoryImpl): LaunchRepository

    @Binds
    fun bindLaunchLocalDataSource(impl: RoomLaunchLocalDataSource): LaunchLocalDataSource

    @Binds
    fun bindLaunchRemoteDataSource(impl: RetrofitLaunchRemoteDataSource): LaunchRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object LaunchesInfrastructureModule {

    @Provides
    @Singleton
    fun provideLaunchesDatabase(@ApplicationContext context: Context): LaunchesDatabase =
        Room.databaseBuilder(context, LaunchesDatabase::class.java, LaunchesDatabase.NAME)
            // A disposable mirror of Launch Library 2: rebuilding it costs one refresh.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideLaunchDao(database: LaunchesDatabase): LaunchDao = database.launchDao()

    @Provides
    @Singleton
    @LaunchLibraryApi
    fun provideLaunchLibraryRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.LAUNCH_LIBRARY_BASE_URL)

    /**
     * `:core:network` already budgets the production host; this covers the dev host debug builds
     * use, so development traffic is held to the same 15 calls an hour.
     */
    @Provides
    @IntoSet
    fun provideLaunchLibraryRateBudget(): HostRateBudget =
        HostRateBudget.LAUNCH_LIBRARY.copy(host = URI(BuildConfig.LAUNCH_LIBRARY_BASE_URL).host)

    @Provides
    @Singleton
    fun provideLaunchLibraryService(@LaunchLibraryApi retrofit: Retrofit): LaunchLibraryService =
        retrofit.create()
}
