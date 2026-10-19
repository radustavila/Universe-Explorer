package com.radustavila.nasaexplorer.core.network.di

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.network.connectivity.AndroidNetworkMonitor
import com.radustavila.nasaexplorer.core.network.http.NetworkConfiguration
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateLimiter
import com.radustavila.nasaexplorer.core.network.ratelimit.RateLimitInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
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

    /**
     * Quotas known up front. Data modules add their own hosts with `@Provides @IntoSet
     * HostRateBudget`; see [HostRateBudget].
     */
    @Provides
    @ElementsIntoSet
    fun provideKnownRateBudgets(): Set<HostRateBudget> = HostRateBudget.KNOWN

    @Provides
    @Singleton
    fun provideHostRateLimiter(
        budgets: Set<@JvmSuppressWildcards HostRateBudget>,
        timeProvider: TimeProvider,
    ): HostRateLimiter = HostRateLimiter(budgets = budgets, timeProvider = timeProvider)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        configuration: NetworkConfiguration,
        rateLimiter: HostRateLimiter,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(configuration.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(configuration.readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(configuration.callTimeoutSeconds, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // First, so a refused call is never logged as if it had been sent.
            .addInterceptor(RateLimitInterceptor(rateLimiter))
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
