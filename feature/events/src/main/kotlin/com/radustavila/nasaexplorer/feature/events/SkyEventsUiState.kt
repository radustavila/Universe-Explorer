package com.radustavila.nasaexplorer.feature.events

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Immutable
data class SkyEventsUiState(
    val month: YearMonth,
    /** True while the month shown is not the current one, so "this month" is offered. */
    val isCurrentMonth: Boolean = true,
    /** True until the month's events are computed. */
    val isLoading: Boolean = true,
    /** The month's events that pass [filters], by week. */
    val weeks: List<SkyEventWeek> = emptyList(),
    /** Categories shown; empty means all. */
    val filters: Set<SkyEventCategory> = emptySet(),
    /** False until the saved place has been read. */
    val isLocationLoaded: Boolean = false,
    /** Null without a saved place: events then carry only their global facts. */
    val location: ObserverLocation? = null,
    /** The reminder lead of every event that has one, by event id. */
    val reminders: Map<String, SkyEventReminderLead> = emptyMap(),
    /** The event whose sheet is open. */
    val selected: SkyEvent? = null,
    /** Events before this are over: dimmed, and no reminder can be set. */
    val now: Instant = Instant.EPOCH,
)

/** The days of one calendar week that fall in the month shown, and their events. */
@Immutable
data class SkyEventWeek(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val events: List<SkyEvent>,
)
