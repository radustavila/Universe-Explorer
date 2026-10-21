package com.radustavila.nasaexplorer.core.network.di

import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.network.storage.HttpCacheStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface NetworkStorageModule {

    @Binds
    @IntoSet
    fun bindHttpCacheUsage(impl: HttpCacheStorage): StorageUsageSource

    @Binds
    @IntoSet
    fun bindHttpCacheCleaner(impl: HttpCacheStorage): CacheCleaner
}
