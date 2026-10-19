package com.radustavila.nasaexplorer.core.cache.di

import com.radustavila.nasaexplorer.core.cache.CacheEvictor
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
interface CacheModule {

    /** Declared so the worker can be created before any data module contributes an evictor. */
    @Multibinds
    fun cacheEvictors(): Set<CacheEvictor>
}
