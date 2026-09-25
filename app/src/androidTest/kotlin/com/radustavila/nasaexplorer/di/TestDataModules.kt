package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.network.di.NetworkBindingModule
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.data.nasa.di.NasaDataModule
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

    @Provides
    @Singleton
    fun provideFakeApodRepository(): FakeApodRepository = FakeApodRepository()

    @Provides
    fun provideApodRepository(fake: FakeApodRepository): ApodRepository = fake

    @Provides
    @Singleton
    fun provideFakeSyncScheduler(): FakeSyncScheduler = FakeSyncScheduler()

    @Provides
    fun provideSyncScheduler(fake: FakeSyncScheduler): SyncScheduler = fake
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
