package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.database.di.FavoritesModule
import com.radustavila.nasaexplorer.core.datastore.di.DataStoreBindingModule
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.network.di.NetworkBindingModule
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import com.radustavila.nasaexplorer.data.launches.di.LaunchesDataModule
import com.radustavila.nasaexplorer.data.mars.di.MarsDataModule
import com.radustavila.nasaexplorer.data.nasa.di.NasaDataModule
import com.radustavila.nasaexplorer.data.nasaimages.di.NasaImagesDataModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Replaces the two bindings that would otherwise make an instrumentation test depend on the
 * network: the repository (now an in-memory cache the test seeds) and the connectivity monitor
 * (now a switch the test flips). Everything above them - navigation, ViewModels, Compose - is the
 * production code.
 *
 * The fakes are also exposed under their concrete type so a test can inject and drive them.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NasaDataModule::class])
object TestNasaDataModule {

    /** Shared with [TestFavoritesModule], so a heart toggled anywhere marks the fake's entries. */
    @Provides
    @Singleton
    fun provideFakeFavoritesRepository(): FakeFavoritesRepository = FakeFavoritesRepository()

    @Provides
    @Singleton
    fun provideFakeApodRepository(favorites: FakeFavoritesRepository): FakeApodRepository =
        FakeApodRepository(favorites)

    @Provides
    fun provideApodRepository(fake: FakeApodRepository): ApodRepository = fake

    @Provides
    @Singleton
    fun provideFakeSyncScheduler(): FakeSyncScheduler = FakeSyncScheduler()

    @Provides
    fun provideSyncScheduler(fake: FakeSyncScheduler): SyncScheduler = fake
}

/** Launches start empty, so the next-launches card stays hidden unless a test seeds the fake. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LaunchesDataModule::class])
object TestLaunchesDataModule {

    @Provides
    @Singleton
    fun provideFakeLaunchRepository(): FakeLaunchRepository = FakeLaunchRepository()

    @Provides
    fun provideLaunchRepository(fake: FakeLaunchRepository): LaunchRepository = fake
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FavoritesModule::class])
object TestFavoritesModule {

    @Provides
    fun provideFavoritesRepository(fake: FakeFavoritesRepository): FavoritesRepository = fake
}

/**
 * Keeps the Universe gallery off the network: rails the test seeds, and no real library calls
 * (nor the library's cache evictor, which this module also contributes).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NasaImagesDataModule::class])
object TestNasaImagesDataModule {

    @Provides
    @Singleton
    fun provideFakeNasaMediaRepository(): FakeNasaMediaRepository = FakeNasaMediaRepository()

    @Provides
    fun provideNasaMediaRepository(fake: FakeNasaMediaRepository): NasaMediaRepository = fake
}

/**
 * Keeps the Mars rail off the network and off WorkManager: photos the test seeds, and sync requests
 * that are only counted (nor the photo cache's evictor, which this module also contributes).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [MarsDataModule::class])
object TestMarsDataModule {

    @Provides
    @Singleton
    fun provideFakeMarsPhotoRepository(): FakeMarsPhotoRepository = FakeMarsPhotoRepository()

    @Provides
    fun provideMarsPhotoRepository(fake: FakeMarsPhotoRepository): MarsPhotoRepository = fake

    @Provides
    @Singleton
    fun provideFakeMarsSyncScheduler(): FakeMarsSyncScheduler = FakeMarsSyncScheduler()

    @Provides
    fun provideMarsSyncScheduler(fake: FakeMarsSyncScheduler): MarsSyncScheduler = fake
}

/**
 * Every test builds a fresh Hilt component, and with it a second DataStore on the same file while
 * the previous one is still alive, which DataStore refuses. Per-source sync bookkeeping (the
 * Universe gallery records its refreshes) and preferences are kept in memory instead.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreBindingModule::class])
object TestDataStoreModule {

    @Provides
    @Singleton
    fun provideSyncMetadataRepository(): SyncMetadataRepository = FakeSyncMetadataRepository()

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(): UserPreferencesRepository = FakeUserPreferencesRepository()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkBindingModule::class])
object TestNetworkBindingModule {

    @Provides
    @Singleton
    fun provideFakeNetworkMonitor(): FakeNetworkMonitor = FakeNetworkMonitor()

    @Provides
    fun provideNetworkMonitor(fake: FakeNetworkMonitor): NetworkMonitor = fake
}
