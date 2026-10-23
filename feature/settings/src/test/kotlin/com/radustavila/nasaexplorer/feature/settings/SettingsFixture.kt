package com.radustavila.nasaexplorer.feature.settings

import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.usecase.CheckAuroraAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.CheckEarthquakeAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.CheckStargazingAlertUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ClearCacheUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.MeasureStorageUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveEarthquakeAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveStargazingAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUnitPreferencesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RescheduleIssPassReminderUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetMeasurementSystemUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetEarthquakeAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetStargazingAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetStargazingAlertMinimumScoreUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetTimeFormatUseCase
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeAuroraAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeCacheStorage
import com.radustavila.nasaexplorer.core.testing.fake.FakeCloudForecastRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeDarkSkyCalculator
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEarthquakeRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeHomeWidgetUpdater
import com.radustavila.nasaexplorer.core.testing.fake.FakeIssPassReminderScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatellitePassPredictor
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSpaceWeatherSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertNotifier
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeStargazingAlertScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository

/** A [SettingsViewModel] over fakes, shared by the ViewModel and screen tests. */
internal class SettingsFixture {
    val clock = FakeTimeProvider()
    val preferences = FakeUserPreferencesRepository()
    val aurora = FakeAuroraAlertRepository()
    val spaceWeatherScheduler = FakeSpaceWeatherSyncScheduler()
    val skyPreferences = FakeSkyPreferencesRepository()
    val location = FakeObserverLocationRepository()
    val stargazing = FakeStargazingAlertRepository()
    val stargazingScheduler = FakeStargazingAlertScheduler()
    val earthquakes = FakeEarthquakeAlertRepository()
    val metadata = FakeSyncMetadataRepository()
    val syncScheduler = FakeSyncScheduler()
    val widgets = FakeHomeWidgetUpdater()
    val images = FakeCacheStorage(StorageKind.IMAGE_CACHE, bytes = 40_000_000)
    val http = FakeCacheStorage(StorageKind.HTTP_CACHE, bytes = 1_000_000)
    val feeds = FakeCacheStorage(StorageKind.CACHED_DATA, bytes = 900_000, keptBytes = 200_000)

    /** Measured, never a cleaner: "Clear cache" must not reach offline favourites. */
    val offlineFavorites = FakeCacheStorage(StorageKind.OFFLINE_FAVORITES, bytes = 3_000_000)

    fun createViewModel(): SettingsViewModel {
        val checkAurora = CheckAuroraAlertUseCase(aurora, FakeSpaceWeatherRepository(), FakeAuroraAlertNotifier(), clock)
        val reschedule = RescheduleIssPassReminderUseCase(
            preferences = skyPreferences,
            locationRepository = location,
            satelliteRepository = FakeSatelliteRepository(),
            predictor = FakeSatellitePassPredictor(),
            scheduler = FakeIssPassReminderScheduler(),
            timeProvider = clock,
        )
        val checkStargazing = CheckStargazingAlertUseCase(
            alertRepository = stargazing,
            locationRepository = location,
            forecastRepository = FakeCloudForecastRepository(),
            calculator = FakeDarkSkyCalculator(),
            notifier = FakeStargazingAlertNotifier(),
            timeProvider = clock,
        )
        return SettingsViewModel(
            observeUnitPreferences = ObserveUnitPreferencesUseCase(preferences),
            observeNewApodNotifications = ObserveNewApodNotificationsEnabledUseCase(preferences),
            observeAuroraAlerts = ObserveAuroraAlertSettingsUseCase(aurora),
            observeIssReminders = ObserveIssPassRemindersEnabledUseCase(skyPreferences),
            observeStargazingAlerts = ObserveStargazingAlertSettingsUseCase(stargazing),
            observeObserverLocation = ObserveObserverLocationUseCase(location),
            observeDailyWallpaper = ObserveDailyWallpaperEnabledUseCase(preferences),
            setMeasurementSystem = SetMeasurementSystemUseCase(preferences),
            setTimeFormat = SetTimeFormatUseCase(preferences, widgets),
            setNewApodNotifications = SetNewApodNotificationsEnabledUseCase(preferences),
            setAuroraAlertEnabled = SetAuroraAlertEnabledUseCase(aurora, spaceWeatherScheduler, checkAurora),
            setAuroraAlertLocation = SetAuroraAlertLocationUseCase(aurora, checkAurora),
            setIssReminders = SetIssPassRemindersEnabledUseCase(skyPreferences, reschedule),
            setStargazingAlertEnabled =
                SetStargazingAlertEnabledUseCase(stargazing, stargazingScheduler, checkStargazing),
            setStargazingAlertMinimumScore = SetStargazingAlertMinimumScoreUseCase(stargazing),
            setDailyWallpaper = SetDailyWallpaperEnabledUseCase(preferences, metadata, syncScheduler),
            measureStorage = MeasureStorageUseCase(setOf(images, http, feeds, offlineFavorites)),
            clearCache = ClearCacheUseCase(setOf(images, http, feeds), metadata),
            observeEarthquakeAlerts = ObserveEarthquakeAlertEnabledUseCase(earthquakes),
            setEarthquakeAlertEnabled = SetEarthquakeAlertEnabledUseCase(
                earthquakes,
                CheckEarthquakeAlertUseCase(
                    earthquakes,
                    location,
                    FakeEarthquakeRepository(),
                    FakeEarthquakeAlertNotifier(),
                    clock,
                ),
            ),
        )
    }

}
