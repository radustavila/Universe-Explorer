package com.radustavila.nasaexplorer.core.network.di

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.network.connectivity.AndroidNetworkMonitor
import com.radustavila.nasaexplorer.core.network.http.NetworkConfiguration
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(configuration: NetworkConfiguration): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(configuration.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(configuration.readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(configuration.callTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (configuration.isLoggingEnabled) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun provideCallFactory(client: OkHttpClient): Call.Factory = client

    @Provides
    @Singleton
    fun provideRetrofitFactory(json: Json, callFactory: Call.Factory): RetrofitFactory =
        RetrofitFactory(json = json, callFactory = callFactory)
}

@Module
@InstallIn(SingletonComponent::class)
interface NetworkBindingModule {

    @Binds
    fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}
