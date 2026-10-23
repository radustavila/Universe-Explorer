package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminder
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderNotifier
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderRepository
import com.radustavila.nasaexplorer.core.domain.launch.LaunchReminderScheduler
import com.radustavila.nasaexplorer.core.model.Launch
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeLaunchReminderRepository : LaunchReminderRepository {

    private val state = MutableStateFlow<Map<String, LaunchReminder>>(emptyMap())

    override val reminders: Flow<Map<String, LaunchReminder>> = state

    val current: Map<String, LaunchReminder> get() = state.value

    override suspend fun setReminder(reminder: LaunchReminder) {
        state.update { it + (reminder.launchId to reminder) }
    }

    override suspend fun removeReminders(launchIds: Set<String>) {
        state.update { it - launchIds }
    }
}

/** Records the armed wake-up instead of scheduling work. */
class FakeLaunchReminderScheduler : LaunchReminderScheduler {

    /** The pending wake-up; null when none is armed. */
    var scheduledAt: Instant? = null
        private set

    override fun scheduleAt(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
    }
}

class FakeLaunchReminderNotifier : LaunchReminderNotifier {

    val notified = mutableListOf<Launch>()

    override suspend fun notifyLiftoffSoon(launch: Launch) {
        notified += launch
    }
}
