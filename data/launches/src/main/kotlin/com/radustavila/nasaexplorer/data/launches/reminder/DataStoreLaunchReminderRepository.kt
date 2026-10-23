package com.radustavila.nasaexplorer.data.launches.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminder
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderRepository
import com.radustavila.nasaexplorer.data.launches.di.LaunchRemindersStore
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The reminders as one string set, `<launch id>|<notified NET millis>|<checked NET millis>` each
 * (empty when not yet). Entries it cannot read are skipped.
 */
@Singleton
class DataStoreLaunchReminderRepository @Inject constructor(
    @LaunchRemindersStore private val dataStore: DataStore<Preferences>,
) : LaunchReminderRepository {

    override val reminders: Flow<Map<String, LaunchReminder>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs.reminders().associateBy { it.launchId } }
        .distinctUntilChanged()

    override suspend fun setReminder(reminder: LaunchReminder) {
        edit { prefs ->
            val others = prefs.reminders().filterNot { it.launchId == reminder.launchId }
            prefs[REMINDERS] = (others + reminder).mapTo(mutableSetOf()) { it.encode() }
        }
    }

    override suspend fun removeReminders(launchIds: Set<String>) {
        edit { prefs ->
            val kept = prefs.reminders().filterNot { it.launchId in launchIds }
            prefs[REMINDERS] = kept.mapTo(mutableSetOf()) { it.encode() }
        }
    }

    private suspend fun edit(transform: (MutablePreferences) -> Unit) {
        try {
            dataStore.edit(transform)
        } catch (_: IOException) {
            // Keeps the previous value; the bell can be rung again.
        }
    }

    private fun Preferences.reminders(): List<LaunchReminder> = this[REMINDERS].orEmpty().mapNotNull { it.decode() }

    private fun LaunchReminder.encode(): String = listOf(
        launchId,
        notifiedNet?.toEpochMilli()?.toString().orEmpty(),
        checkedNet?.toEpochMilli()?.toString().orEmpty(),
    ).joinToString(SEPARATOR)

    private fun String.decode(): LaunchReminder? {
        val parts = split(SEPARATOR)
        val launchId = parts.firstOrNull()?.takeIf(String::isNotEmpty) ?: return null
        return LaunchReminder(
            launchId = launchId,
            notifiedNet = parts.getOrNull(1)?.toLongOrNull()?.let(Instant::ofEpochMilli),
            checkedNet = parts.getOrNull(2)?.toLongOrNull()?.let(Instant::ofEpochMilli),
        )
    }

    private companion object {
        val REMINDERS = stringSetPreferencesKey("reminders")
        const val SEPARATOR = "|"
    }
}
