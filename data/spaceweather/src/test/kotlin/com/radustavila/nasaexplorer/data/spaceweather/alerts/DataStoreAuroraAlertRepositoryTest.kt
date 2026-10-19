package com.radustavila.nasaexplorer.data.spaceweather.alerts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAuroraAlertRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val repository = DataStoreAuroraAlertRepository(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "aurora_alerts.preferences_pb")
        },
    )

    @Test
    fun `alerts are off and placeless until the user chooses`() = scope.runTest {
        assertEquals(AuroraAlertSettings(), repository.settings.first())
        assertNull(repository.alertedUntil())
    }

    @Test
    fun `choices and the announced storm are remembered`() = scope.runTest {
        repository.setEnabled(true)
        repository.setLocation(latitude = 59.3, longitude = 18.1)
        repository.setAlertedUntil(Instant.parse("2026-10-20T00:00:00Z"))

        assertEquals(AuroraAlertSettings(enabled = true, latitude = 59.3, longitude = 18.1), repository.settings.first())
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), repository.alertedUntil())

        repository.setAlertedUntil(null)
        assertNull(repository.alertedUntil())
    }
}
