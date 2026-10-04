package com.radustavila.nasaexplorer.data.nasa.di

import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.data.nasa.BuildConfig
import com.radustavila.nasaexplorer.data.nasa.remote.NasaApodService
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
annotation class NasaApi

@Module
@InstallIn(SingletonComponent::class)
object NasaNetworkModule {

    /** science.nasa.gov serves APOD publicly: no API key, so no authenticating interceptor. */
    @Provides
    @Singleton
    @NasaApi
    fun provideNasaRetrofit(retrofitFactory: RetrofitFactory): Retrofit =
        retrofitFactory.create(baseUrl = BuildConfig.NASA_BASE_URL)

    @Provides
    @Singleton
    fun provideNasaApodService(@NasaApi retrofit: Retrofit): NasaApodService = retrofit.create()
}
