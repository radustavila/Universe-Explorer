package com.radustavila.nasaexplorer.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
}
