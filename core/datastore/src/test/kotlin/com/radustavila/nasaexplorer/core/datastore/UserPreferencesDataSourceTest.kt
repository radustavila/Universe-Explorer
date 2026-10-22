package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var userPreferences: UserPreferencesDataSource

    @Before
    fun setUp() {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { temporaryFolder.newFile("user_preferences.preferences_pb") },
        )
        userPreferences = UserPreferencesDataSource(dataStore)
    }

    @Test
    fun `new APOD notifications are off until the user opts in`() = runTest {
        assertFalse(userPreferences.newApodNotificationsEnabled.first())
    }

    @Test
    fun `persists the notification choice`() = runTest {
        userPreferences.setNewApodNotificationsEnabled(true)
        assertTrue(userPreferences.newApodNotificationsEnabled.first())

        userPreferences.setNewApodNotificationsEnabled(false)
        assertFalse(userPreferences.newApodNotificationsEnabled.first())
    }

    @Test
    fun `the daily wallpaper is off until the user opts in, then persists`() = runTest {
        assertFalse(userPreferences.dailyWallpaperEnabled.first())

        userPreferences.setDailyWallpaperEnabled(true)
        assertTrue(userPreferences.dailyWallpaperEnabled.first())
        // The two choices are independent.
        assertFalse(userPreferences.newApodNotificationsEnabled.first())
    }

    @Test
    fun `units follow the device until the user picks, then persist`() = runTest {
        assertEquals(UnitPreferences(MeasurementSystem.AUTOMATIC, TimeFormat.SYSTEM), userPreferences.unitPreferences.first())

        userPreferences.setMeasurementSystem(MeasurementSystem.IMPERIAL)
        userPreferences.setTimeFormat(TimeFormat.TWENTY_FOUR_HOUR)

        assertEquals(
            UnitPreferences(MeasurementSystem.IMPERIAL, TimeFormat.TWENTY_FOUR_HOUR),
            userPreferences.unitPreferences.first(),
        )
        assertFalse(userPreferences.dailyWallpaperEnabled.first())
    }
}
