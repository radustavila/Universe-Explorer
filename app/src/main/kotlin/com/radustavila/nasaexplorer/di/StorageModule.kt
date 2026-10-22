package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.storage.DatabaseStorage
import com.radustavila.nasaexplorer.storage.ImageCacheStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface StorageModule {

    @Binds
    @IntoSet
    fun bindImageCacheUsage(impl: ImageCacheStorage): StorageUsageSource

    @Binds
    @IntoSet
    fun bindImageCacheCleaner(impl: ImageCacheStorage): CacheCleaner

    @Binds
    @IntoSet
    fun bindDatabaseUsage(impl: DatabaseStorage): StorageUsageSource
}
