package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.database.di.FavoritesModule
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.network.di.NetworkBindingModule
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.data.launches.di.LaunchesDataModule
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

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkBindingModule::class])
object TestNetworkBindingModule {

    @Provides
    @Singleton
    fun provideFakeNetworkMonitor(): FakeNetworkMonitor = FakeNetworkMonitor()

    @Provides
    fun provideNetworkMonitor(fake: FakeNetworkMonitor): NetworkMonitor = fake
}
