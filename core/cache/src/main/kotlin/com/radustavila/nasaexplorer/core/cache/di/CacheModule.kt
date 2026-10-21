package com.radustavila.nasaexplorer.core.cache.di

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import com.radustavila.nasaexplorer.core.cache.EvictingCacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
interface CacheModule {

    /** Declared so the worker can be created before any data module contributes an evictor. */
    @Multibinds
    fun cacheEvictors(): Set<CacheEvictor>

    /** What Settings measures; contributed next to each storage (images, HTTP, databases, …). */
    @Multibinds
    fun storageUsageSources(): Set<StorageUsageSource>

    /** What "Clear cache" empties; never favourites or their offline copies. */
    @Multibinds
    fun cacheCleaners(): Set<CacheCleaner>

    @Binds
    @IntoSet
    fun bindEvictingCacheCleaner(impl: EvictingCacheCleaner): CacheCleaner
}
