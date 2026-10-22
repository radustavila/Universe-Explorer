package com.radustavila.nasaexplorer.data.skyevents.reminder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminder
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSkyEventReminderRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val dataStore by lazy {
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            File(folder.root, "sky_event_reminders.preferences_pb")
        }
    }
    private val repository by lazy { DataStoreSkyEventReminderRepository(dataStore) }

    @Test
    fun `no reminders until the user picks an event`() = scope.runTest {
        assertTrue(repository.reminders.first().isEmpty())
    }

    @Test
    fun `reminders round-trip, one per event, the last lead winning`() = scope.runTest {
        repository.setReminder(SkyEventReminder(ECLIPSE, SkyEventReminderLead.ONE_DAY))
        repository.setReminder(SkyEventReminder(PERSEIDS, SkyEventReminderLead.ONE_HOUR))
        repository.setReminder(SkyEventReminder(ECLIPSE, SkyEventReminderLead.FIFTEEN_MINUTES))

        assertEquals(
            mapOf(
                ECLIPSE to SkyEventReminder(ECLIPSE, SkyEventReminderLead.FIFTEEN_MINUTES),
                PERSEIDS to SkyEventReminder(PERSEIDS, SkyEventReminderLead.ONE_HOUR),
            ),
            repository.reminders.first(),
        )

        repository.removeReminders(setOf(ECLIPSE, "unknown"))
        assertEquals(setOf(PERSEIDS), repository.reminders.first().keys)
    }

    @Test
    fun `entries it cannot read are skipped`() = scope.runTest {
        dataStore.edit { it[stringSetPreferencesKey("reminders")] = setOf(
            "$ECLIPSE|ONE_WEEK",
            "garbage",
            "$PERSEIDS|ONE_DAY",
        ) }

        assertEquals(setOf(PERSEIDS), repository.reminders.first().keys)
    }

    private companion object {
        const val ECLIPSE = "solar-eclipse:2026-08-12"
        const val PERSEIDS = "meteors-perseids:2026-08-13"
    }
}
