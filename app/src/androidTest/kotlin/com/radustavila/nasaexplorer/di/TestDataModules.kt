package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.database.di.FavoritesModule
import com.radustavila.nasaexplorer.core.datastore.di.DataStoreBindingModule
import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderRepository
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderScheduler
import com.radustavila.nasaexplorer.core.domain.location.CityDirectory
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationProvider
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.repository.ApodRepository
import com.radustavila.nasaexplorer.core.domain.repository.AuroraAlertRepository
import com.radustavila.nasaexplorer.core.domain.repository.EarthEventRepository
import com.radustavila.nasaexplorer.core.domain.repository.EpicRepository
import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.domain.repository.MarsPhotoRepository
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.domain.repository.NewsRepository
import com.radustavila.nasaexplorer.core.domain.repository.SmallBodiesRepository
import com.radustavila.nasaexplorer.core.domain.repository.SpaceWeatherRepository
import com.radustavila.nasaexplorer.core.domain.repository.SyncMetadataRepository
import com.radustavila.nasaexplorer.core.domain.repository.TodayCardsRepository
import com.radustavila.nasaexplorer.core.domain.repository.UserPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.IssPassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sync.EpicSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.ExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.MarsSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SmallBodiesSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.domain.sync.SyncScheduler
import com.radustavila.nasaexplorer.core.location.di.LocationModule
import com.radustavila.nasaexplorer.core.network.di.NetworkBindingModule
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCityDirectory
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeviceLocationProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthEventRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeFavoritesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeNewsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSmallBodiesSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTodayCardsRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import com.radustavila.nasaexplorer.data.earthevents.di.EarthEventsDataModule
import com.radustavila.nasaexplorer.data.epic.di.EpicDataModule
import com.radustavila.nasaexplorer.data.exoplanets.di.ExoplanetsDataModule
import com.radustavila.nasaexplorer.data.launches.di.LaunchesDataModule
import com.radustavila.nasaexplorer.data.mars.di.MarsDataModule
import com.radustavila.nasaexplorer.data.nasa.di.NasaDataModule
import com.radustavila.nasaexplorer.data.nasaimages.di.NasaImagesDataModule
import com.radustavila.nasaexplorer.data.news.di.NewsDataModule
import com.radustavila.nasaexplorer.data.sky.di.SkyDataModule
import com.radustavila.nasaexplorer.data.smallbodies.di.SmallBodiesDataModule
import com.radustavila.nasaexplorer.data.spaceweather.di.SpaceWeatherDataModule
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

/**
 * Launches start empty, so the next-launches card stays hidden unless a test seeds the fake; so
 * does the crew list, which shares the Launch Library 2 module. Launch reminders stay in memory,
 * without WorkManager.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LaunchesDataModule::class])
object TestLaunchesDataModule {

    @Provides
    @Singleton
    fun provideFakeLaunchRepository(): FakeLaunchRepository = FakeLaunchRepository()

    @Provides
    fun provideLaunchRepository(fake: FakeLaunchRepository): LaunchRepository = fake

    @Provides
    @Singleton
    fun provideFakePeopleInSpaceRepository(): FakePeopleInSpaceRepository = FakePeopleInSpaceRepository()

    @Provides
    fun providePeopleInSpaceRepository(fake: FakePeopleInSpaceRepository): PeopleInSpaceRepository = fake

    @Provides
    @Singleton
    fun provideLaunchReminderRepository(): LaunchReminderRepository = FakeLaunchReminderRepository()

    @Provides
    @Singleton
    fun provideLaunchReminderScheduler(): LaunchReminderScheduler = FakeLaunchReminderScheduler()
}

/**
 * Sky tonight without CelesTrak, wheretheiss.at or WorkManager: orbital elements the test seeds,
 * reminders kept in memory. The astronomy itself (Astronomy Engine, SGP4) stays real.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SkyDataModule::class])
object TestSkyDataModule {

    @Provides
    @Singleton
    fun provideFakeSatelliteRepository(): FakeSatelliteRepository = FakeSatelliteRepository()

    @Provides
    fun provideSatelliteRepository(fake: FakeSatelliteRepository): SatelliteRepository = fake

    @Provides
    @Singleton
    fun provideSkyPreferencesRepository(): SkyPreferencesRepository = FakeSkyPreferencesRepository()

    @Provides
    @Singleton
    fun provideIssPassReminderScheduler(): IssPassReminderScheduler = FakeIssPassReminderScheduler()

    @Provides
    @Singleton
    fun provideSatellitePassReminderScheduler(): SatellitePassReminderScheduler = FakeSatellitePassReminderScheduler()
}

/** The observer's location in memory (no DataStore, no platform location). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [LocationModule::class])
object TestLocationModule {

    @Provides
    @Singleton
    fun provideFakeObserverLocationRepository(): FakeObserverLocationRepository = FakeObserverLocationRepository()

    @Provides
    fun provideObserverLocationRepository(fake: FakeObserverLocationRepository): ObserverLocationRepository = fake

    @Provides
    @Singleton
    fun provideDeviceLocationProvider(): DeviceLocationProvider = FakeDeviceLocationProvider()

    @Provides
    fun provideCityDirectory(): CityDirectory = FakeCityDirectory()
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FavoritesModule::class])
object TestFavoritesModule {

    @Provides
    fun provideFavoritesRepository(fake: FakeFavoritesRepository): FavoritesRepository = fake
}

/**
 * Earth events and quakes start empty, so the Today card stays hidden unless a test seeds the fake;
 * no EONET or USGS calls (nor the events cache evictor or search sources, which this module also
 * contributes). The strong-quake alert stays off.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [EarthEventsDataModule::class])
object TestEarthEventsDataModule {

    @Provides
    @Singleton
    fun provideFakeEarthEventRepository(): FakeEarthEventRepository = FakeEarthEventRepository()

    @Provides
    fun provideEarthEventRepository(fake: FakeEarthEventRepository): EarthEventRepository = fake

    @Provides
    @Singleton
    fun provideEarthquakeRepository(): EarthquakeRepository = FakeEarthquakeRepository()

    @Provides
    @Singleton
    fun provideEarthquakeAlertRepository(): EarthquakeAlertRepository = FakeEarthquakeAlertRepository()
}

/**
 * News starts empty, so the Today card stays hidden unless a test seeds the fake; no feed calls (nor
 * the news cache evictor or search source, which this module also contributes).
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NewsDataModule::class])
object TestNewsDataModule {

    @Provides
    @Singleton
    fun provideFakeNewsRepository(): FakeNewsRepository = FakeNewsRepository()

    @Provides
    fun provideNewsRepository(fake: FakeNewsRepository): NewsRepository = fake
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

    @Provides
    @Singleton
    fun provideTodayCardsRepository(): TodayCardsRepository = FakeTodayCardsRepository()
}

/**
 * Keeps the Earth tab and the Today screen's Earth card off the network too: photos the test seeds,
 * and a scheduler that never runs.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [EpicDataModule::class])
object TestEpicDataModule {

    @Provides
    @Singleton
    fun provideFakeEpicRepository(): FakeEpicRepository = FakeEpicRepository()

    @Provides
    fun provideEpicRepository(fake: FakeEpicRepository): EpicRepository = fake

    @Provides
    @Singleton
    fun provideFakeEpicSyncScheduler(): FakeEpicSyncScheduler = FakeEpicSyncScheduler()

    @Provides
    fun provideEpicSyncScheduler(fake: FakeEpicSyncScheduler): EpicSyncScheduler = fake
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

/**
 * Keeps NOAA, DONKI and the space weather database out of the end-to-end test, along with the alert
 * settings' DataStore (a second Hilt component would open it twice) and the 30-minute job.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SpaceWeatherDataModule::class])
object TestSpaceWeatherDataModule {

    @Provides
    @Singleton
    fun provideFakeSpaceWeatherRepository(): FakeSpaceWeatherRepository = FakeSpaceWeatherRepository()

    @Provides
    fun provideSpaceWeatherRepository(fake: FakeSpaceWeatherRepository): SpaceWeatherRepository = fake

    @Provides
    @Singleton
    fun provideFakeSpaceWeatherSyncScheduler(): FakeSpaceWeatherSyncScheduler = FakeSpaceWeatherSyncScheduler()

    @Provides
    fun provideSpaceWeatherSyncScheduler(fake: FakeSpaceWeatherSyncScheduler): SpaceWeatherSyncScheduler = fake

    @Provides
    @Singleton
    fun provideAuroraAlertRepository(): AuroraAlertRepository = FakeAuroraAlertRepository()
}

/** Keeps JPL and the asteroid database out of the end-to-end test. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SmallBodiesDataModule::class])
object TestSmallBodiesDataModule {

    @Provides
    @Singleton
    fun provideFakeSmallBodiesRepository(): FakeSmallBodiesRepository = FakeSmallBodiesRepository()

    @Provides
    fun provideSmallBodiesRepository(fake: FakeSmallBodiesRepository): SmallBodiesRepository = fake

    @Provides
    @Singleton
    fun provideFakeSmallBodiesSyncScheduler(): FakeSmallBodiesSyncScheduler = FakeSmallBodiesSyncScheduler()

    @Provides
    fun provideSmallBodiesSyncScheduler(fake: FakeSmallBodiesSyncScheduler): SmallBodiesSyncScheduler = fake
}

/** Keeps a catalogue cached on the device (or the archive itself) out of the end-to-end test. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExoplanetsDataModule::class])
object TestExoplanetsDataModule {

    @Provides
    @Singleton
    fun provideFakeExoplanetRepository(): FakeExoplanetRepository = FakeExoplanetRepository()

    @Provides
    fun provideExoplanetRepository(fake: FakeExoplanetRepository): ExoplanetRepository = fake

    @Provides
    @Singleton
    fun provideFakeExoplanetSyncScheduler(): FakeExoplanetSyncScheduler = FakeExoplanetSyncScheduler()

    @Provides
    fun provideExoplanetSyncScheduler(fake: FakeExoplanetSyncScheduler): ExoplanetSyncScheduler = fake
}
