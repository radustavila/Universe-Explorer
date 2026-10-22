package com.radustavila.nasaexplorer.data.skyevents.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminder
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderRepository
import com.radustavila.nasaexplorer.data.skyevents.di.SkyEventRemindersStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reminders as one string set, `<event id>|<lead name>` each. Entries it cannot read (a lead
 * dropped in a later version) are skipped.
 */
@Singleton
class DataStoreSkyEventReminderRepository @Inject constructor(
    @SkyEventRemindersStore private val dataStore: DataStore<Preferences>,
) : SkyEventReminderRepository {

    override val reminders: Flow<Map<String, SkyEventReminder>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.reminders().associateBy { it.eventId } }
        .distinctUntilChanged()

    override suspend fun setReminder(reminder: SkyEventReminder) {
        edit { prefs ->
            val others = prefs.reminders().filterNot { it.eventId == reminder.eventId }
            prefs[REMINDERS] = (others + reminder).mapTo(mutableSetOf()) { it.encode() }
        }
    }

    override suspend fun removeReminders(eventIds: Set<String>) {
        edit { prefs ->
            val kept = prefs.reminders().filterNot { it.eventId in eventIds }
            prefs[REMINDERS] = kept.mapTo(mutableSetOf()) { it.encode() }
        }
    }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (_: IOException) {
            // Keeps the previous value; the reminder can be set again.
        }
    }

    private fun Preferences.reminders(): List<SkyEventReminder> = this[REMINDERS].orEmpty().mapNotNull { it.decode() }

    private fun SkyEventReminder.encode(): String = "$eventId$SEPARATOR${lead.name}"

    private fun String.decode(): SkyEventReminder? {
        val eventId = substringBeforeLast(SEPARATOR, missingDelimiterValue = "")
        val lead = SkyEventReminderLead.entries.firstOrNull { it.name == substringAfterLast(SEPARATOR) }
        return if (eventId.isEmpty() || lead == null) null else SkyEventReminder(eventId, lead)
    }

    private companion object {
        val REMINDERS = stringSetPreferencesKey("reminders")
        const val SEPARATOR = '|'
    }
}
