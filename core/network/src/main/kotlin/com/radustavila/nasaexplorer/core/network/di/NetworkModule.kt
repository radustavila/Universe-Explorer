package com.radustavila.nasaexplorer.core.network.di

import android.content.Context
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.network.connectivity.AndroidNetworkMonitor
import com.radustavila.nasaexplorer.core.network.http.NetworkConfiguration
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.http.apiHttpClient
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateBudget
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateLimiter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient

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
        @ApplicationContext context: Context,
        configuration: NetworkConfiguration,
        rateLimiter: HostRateLimiter,
    ): OkHttpClient = apiHttpClient(configuration, rateLimiter, cacheDirectory = context.cacheDir.resolve("http"))

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
