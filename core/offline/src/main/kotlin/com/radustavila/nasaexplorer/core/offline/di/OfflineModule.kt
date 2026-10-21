package com.radustavila.nasaexplorer.core.offline.di

import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoriteImages
import com.radustavila.nasaexplorer.core.domain.offline.OfflineFavoritesStorage
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.offline.OfflineFavoritesStorageUsage
import com.radustavila.nasaexplorer.core.offline.RoomOfflineFavoritesStorage
import com.radustavila.nasaexplorer.core.offline.WorkManagerOfflineFavoriteImages
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface OfflineModule {

    @Binds
    fun bindOfflineFavoriteImages(impl: WorkManagerOfflineFavoriteImages): OfflineFavoriteImages

    @Binds
    fun bindOfflineFavoritesStorage(impl: RoomOfflineFavoritesStorage): OfflineFavoritesStorage

    /** Shown in Settings' storage overview; never cleared by "Clear cache". */
    @Binds
    @IntoSet
    fun bindOfflineFavoritesStorageUsage(impl: OfflineFavoritesStorageUsage): StorageUsageSource
}
