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
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class NasaApi

@Module
@InstallIn(SingletonComponent::class)
object NasaNetworkModule {

    /**
     * NASA authenticates with an `api_key` query parameter. Adding it in an interceptor keeps the
     * key out of every call site (and out of the service interface).
     */
    @Provides
    @Singleton
    @NasaApi
    fun provideApiKeyInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val authenticatedUrl = request.url.newBuilder()
            .addQueryParameter("api_key", BuildConfig.NASA_API_KEY)
            .build()
        chain.proceed(request.newBuilder().url(authenticatedUrl).build())
    }

    @Provides
    @Singleton
    @NasaApi
    fun provideNasaCallFactory(
        client: OkHttpClient,
        @NasaApi apiKeyInterceptor: Interceptor,
    ): Call.Factory = client.newBuilder()
        .addInterceptor(apiKeyInterceptor)
        .build()

    @Provides
    @Singleton
    @NasaApi
    fun provideNasaRetrofit(
        retrofitFactory: RetrofitFactory,
        @NasaApi callFactory: Call.Factory,
    ): Retrofit = retrofitFactory.create(
        baseUrl = BuildConfig.NASA_BASE_URL,
        callFactory = callFactory,
    )

    @Provides
    @Singleton
    fun provideNasaApodService(@NasaApi retrofit: Retrofit): NasaApodService = retrofit.create()
}
