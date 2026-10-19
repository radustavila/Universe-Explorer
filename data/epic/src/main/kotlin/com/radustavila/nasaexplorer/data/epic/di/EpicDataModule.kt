package com.radustavila.nasaexplorer.data.epic.di

import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.data.epic.BuildConfig
import com.radustavila.nasaexplorer.data.epic.local.EpicLocalDataSource
import com.radustavila.nasaexplorer.data.epic.local.RoomEpicLocalDataSource
import com.radustavila.nasaexplorer.data.epic.prefetch.CoilImagePrefetcher
import com.radustavila.nasaexplorer.data.epic.prefetch.ImagePrefetcher
import com.radustavila.nasaexplorer.data.epic.remote.EpicRemoteDataSource
import com.radustavila.nasaexplorer.data.epic.remote.EpicService
import com.radustavila.nasaexplorer.data.epic.remote.RetrofitEpicRemoteDataSource
import com.radustavila.nasaexplorer.data.epic.repository.EpicRepositoryImpl
import com.radustavila.nasaexplorer.data.epic.sync.WorkManagerEpicSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EpicApi

/** Root of EPIC's image archive; also the API's base URL. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EpicArchiveBaseUrl

/**
 * EPIC's contract bindings. `WorkManager` itself is provided once for the whole app by the APOD
 * data module.
 */
@Module
@InstallIn(SingletonComponent::class)
interface EpicDataModule {

    @Binds
    fun bindEpicRepository(impl: EpicRepositoryImpl): EpicRepository

    @Binds
    fun bindEpicLocalDataSource(impl: RoomEpicLocalDataSource): EpicLocalDataSource

    @Binds
    fun bindEpicRemoteDataSource(impl: RetrofitEpicRemoteDataSource): EpicRemoteDataSource

    @Binds
    fun bindImagePrefetcher(impl: CoilImagePrefetcher): ImagePrefetcher

    @Binds
    fun bindEpicSyncScheduler(impl: WorkManagerEpicSyncScheduler): EpicSyncScheduler
}

@Module
@InstallIn(SingletonComponent::class)
object EpicNetworkModule {

    @Provides
    @EpicArchiveBaseUrl
    fun provideEpicArchiveBaseUrl(): String = BuildConfig.EPIC_BASE_URL

    /** EPIC is public: no key, no authenticating interceptor. */
    @Provides
    @Singleton
    @EpicApi
    fun provideEpicRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.EPIC_BASE_URL)

    @Provides
    @Singleton
    fun provideEpicService(@EpicApi retrofit: Retrofit): EpicService = retrofit.create()
}
