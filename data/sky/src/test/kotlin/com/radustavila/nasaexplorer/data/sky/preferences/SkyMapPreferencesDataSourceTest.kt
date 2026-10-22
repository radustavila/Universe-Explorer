package com.radustavila.nasaexplorer.data.sky.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkyMapPreferencesDataSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `night mode is off until switched on, and kept next to Sky tonight's choices`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { temporaryFolder.newFile("sky_preferences.preferences_pb") },
        )
        val skyMap = SkyMapPreferencesDataSource(dataStore)
        val skyTonight = SkyPreferencesDataSource(dataStore)

        assertFalse(skyMap.observeNightMode().first())
        skyMap.setNightMode(true)
        skyTonight.setIssPassRemindersEnabled(true)

        assertTrue(skyMap.observeNightMode().first())
        assertTrue(skyTonight.issPassRemindersEnabled.first())
    }
}
