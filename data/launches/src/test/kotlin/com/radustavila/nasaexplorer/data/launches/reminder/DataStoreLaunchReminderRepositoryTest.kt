package com.radustavila.nasaexplorer.data.launches.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminder
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLaunchReminderRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "launch_reminders.preferences_pb")
        }
    }
    private val repository by lazy { DataStoreLaunchReminderRepository(dataStore) }

    @Test
    fun `no reminders until the user rings a bell`() = scope.runTest {
        assertTrue(repository.reminders.first().isEmpty())
    }

    @Test
    fun `reminders round-trip with what was already done for which NET`() = scope.runTest {
        val net = Instant.parse("2026-10-24T05:29:00Z")
        repository.setReminder(LaunchReminder(STARLINK))
        repository.setReminder(LaunchReminder(CREW, checkedNet = net))
        repository.setReminder(LaunchReminder(STARLINK, notifiedNet = net, checkedNet = net))

        assertEquals(
            mapOf(
                STARLINK to LaunchReminder(STARLINK, notifiedNet = net, checkedNet = net),
                CREW to LaunchReminder(CREW, checkedNet = net),
            ),
            repository.reminders.first(),
        )

        repository.removeReminders(setOf(STARLINK, "unknown"))
        assertEquals(setOf(CREW), repository.reminders.first().keys)
    }

    @Test
    fun `entries it cannot read are skipped`() = scope.runTest {
        dataStore.edit { it[stringSetPreferencesKey("reminders")] = setOf("", "|1|2", "$CREW|x|") }

        assertEquals(mapOf(CREW to LaunchReminder(CREW)), repository.reminders.first())
    }

    private companion object {
        const val STARLINK = "e2886a96-5ce9-4f45-872f-4e25a00be837"
        const val CREW = "18441371-8b2e-457c-afb5-1ec1b11ab630"
    }
}
