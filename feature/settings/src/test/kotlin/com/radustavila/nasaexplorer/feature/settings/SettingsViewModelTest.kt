package com.radustavila.nasaexplorer.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = SettingsFixture()
    private val preferences = fixture.preferences
    private val aurora = fixture.aurora
    private val spaceWeatherScheduler = fixture.spaceWeatherScheduler
    private val skyPreferences = fixture.skyPreferences
    private val location = fixture.location
    private val syncScheduler = fixture.syncScheduler
    private val images = fixture.images
    private val http = fixture.http
    private val feeds = fixture.feeds

    private fun createViewModel() = fixture.createViewModel()

    @Test
    fun `shows every choice as stored, and measures storage on opening`() = runTest {
        preferences.setMeasurementSystem(MeasurementSystem.IMPERIAL)
        preferences.setNewApodNotificationsEnabled(true)
        skyPreferences.setIssPassRemindersEnabled(true)

        createViewModel().uiState.test {
            val state = awaitUntil { it.storage.entries != null && it.newApodNotificationsEnabled }
            assertEquals(MeasurementSystem.IMPERIAL, state.units.measurementSystem)
            assertTrue(state.issRemindersEnabled)
            assertFalse(state.dailyWallpaperEnabled)
            assertEquals(
                listOf(
                    StorageEntry(StorageKind.IMAGE_CACHE, 40_000_000),
                    StorageEntry(StorageKind.HTTP_CACHE, 1_000_000),
                    StorageEntry(StorageKind.CACHED_DATA, 900_000),
                    StorageEntry(StorageKind.OFFLINE_FAVORITES, 3_000_000),
                ),
                state.storage.entries,
            )
            assertEquals(44_900_000L, state.storage.totalBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unit and clock choices are saved for every screen`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onMeasurementSystemChange(MeasurementSystem.METRIC)
            viewModel.onTimeFormatChange(TimeFormat.TWELVE_HOUR)

            val state = awaitUntil { it.units == UnitPreferences(MeasurementSystem.METRIC, TimeFormat.TWELVE_HOUR) }
            assertEquals(UnitPreferences(MeasurementSystem.METRIC, TimeFormat.TWELVE_HOUR), preferences.currentUnitPreferences)
            assertEquals(MeasurementSystem.METRIC, state.units.measurementSystem)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching the wallpaper and new picture notifications goes through their use cases`() = runTest {
        val viewModel = createViewModel()

        viewModel.onDailyWallpaperChange(true)
        viewModel.onNewApodNotificationsChange(true)

        assertTrue(preferences.currentDailyWallpaperEnabled)
        assertTrue(preferences.currentNewApodNotificationsEnabled)
        // Turning the wallpaper on asks the sync job to set it.
        assertEquals(1, syncScheduler.requestSyncCallCount)
    }

    @Test
    fun `aurora alerts without a place of their own take the saved location`() = runTest {
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()

        viewModel.onAuroraAlertsChange(true)

        val settings = aurora.settings.first()
        assertTrue(settings.enabled)
        assertEquals(TestSky.BUCHAREST.latitude, settings.latitude!!, 0.0)
        assertEquals(1, spaceWeatherScheduler.periodicSyncRegistrations)
    }

    @Test
    fun `aurora alerts with no place at all ask for one and stay off`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onAuroraAlertsChange(true)

            assertEquals(SettingsMessage.AURORA_NEEDS_PLACE, awaitUntil { it.message != null }.message)
            assertFalse(aurora.settings.first().enabled)
            viewModel.onMessageShown()
            awaitUntil { it.message == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the aurora place can be moved to the saved location`() = runTest {
        aurora.setLocation(64.1, -21.9)
        location.setLocation(TestSky.location())
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertTrue(awaitUntil { it.observerLocation != null && it.auroraAlerts.hasLocation }.canUseSavedPlaceForAurora)

            viewModel.onUseSavedPlaceForAurora()

            val state = awaitUntil { it.auroraAlerts.latitude == TestSky.BUCHAREST.latitude }
            assertFalse(state.canUseSavedPlaceForAurora)
            assertEquals(AuroraAlertSettings(enabled = false, latitude = 44.43, longitude = 26.10), state.auroraAlerts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the cache empties every cache, keeps what must stay, and measures again`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitUntil { it.storage.entries != null }
            viewModel.onClearCache()

            val state = awaitUntil { it.message == SettingsMessage.CACHE_CLEARED && !it.storage.isClearing }
            assertEquals(listOf(1, 1, 1), listOf(images.clearCount, http.clearCount, feeds.clearCount))
            // Offline favourites are untouched; the feeds keep their favourite rows.
            assertEquals(0, fixture.offlineFavorites.clearCount)
            assertEquals(3_200_000L, state.storage.totalBytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a cache that cannot be cleared is reported`() = runTest {
        http.failure = IOException("busy")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            viewModel.onClearCache()

            assertEquals(SettingsMessage.CACHE_CLEAR_FAILED, awaitUntil { it.message != null }.message)
            assertEquals(1, images.clearCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<SettingsUiState>.awaitUntil(predicate: (SettingsUiState) -> Boolean): SettingsUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }
}
