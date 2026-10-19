package com.radustavila.nasaexplorer.data.nasa.di

import android.content.Context
import androidx.work.WorkManager
import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.data.nasa.cache.ApodCacheEvictor
import com.radustavila.nasaexplorer.data.nasa.local.ApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.local.RoomApodLocalDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.ApodRemoteDataSource
import com.radustavila.nasaexplorer.data.nasa.remote.RetrofitApodRemoteDataSource
import com.radustavila.nasaexplorer.data.nasa.repository.ApodRepositoryImpl
import com.radustavila.nasaexplorer.data.nasa.sync.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * The only place where domain contracts are bound to their infrastructure implementations. Swapping
 * an implementation (a different backend, an in-memory cache, another scheduler) is a change here
 * and nowhere else.
 */
@Module
@InstallIn(SingletonComponent::class)
interface NasaDataModule {

    @Binds
    fun bindApodRepository(impl: ApodRepositoryImpl): ApodRepository

    @Binds
    fun bindApodLocalDataSource(impl: RoomApodLocalDataSource): ApodLocalDataSource

    @Binds
    fun bindApodRemoteDataSource(impl: RetrofitApodRemoteDataSource): ApodRemoteDataSource

    @Binds
    fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    @IntoSet
    fun bindApodCacheEvictor(impl: ApodCacheEvictor): CacheEvictor
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
