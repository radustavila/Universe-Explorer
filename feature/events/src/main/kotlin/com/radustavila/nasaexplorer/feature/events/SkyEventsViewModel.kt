package com.radustavila.nasaexplorer.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.domain.usecase.FindSkyEventUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyEventRemindersUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyEventsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetSkyEventReminderUseCase
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The calendar, one month at a time in the device's time zone, recomputed on the device whenever
 * the month or the saved place changes. Opened with an event id (a reminder, search, a card), it
 * jumps to that event's month and opens its sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SkyEventsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSkyEvents: ObserveSkyEventsUseCase,
    observeLocation: ObserveObserverLocationUseCase,
    observeReminders: ObserveSkyEventRemindersUseCase,
    private val setReminder: SetSkyEventReminderUseCase,
    private val findEvent: FindSkyEventUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val currentMonth: YearMonth get() = YearMonth.from(timeProvider.now().atZone(zone))

    private val month = MutableStateFlow(currentMonth)
    private val filters = MutableStateFlow(emptySet<SkyEventCategory>())
    private val selected = MutableStateFlow<SkyEvent?>(null)

    private val monthEvents = month.flatMapLatest { shown ->
        observeSkyEvents(
            from = shown.atDay(1).atStartOfDay(zone).toInstant(),
            until = shown.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant(),
        ).map<List<SkyEvent>, MonthEvents> { MonthEvents(shown, it) }
            .onStart { emit(MonthEvents(shown, events = null)) }
    }

    private val place = observeLocation().map { LoadedPlace(it) }.onStart<LoadedPlace?> { emit(null) }

    val uiState: StateFlow<SkyEventsUiState> = combine(
        monthEvents,
        filters,
        place,
        observeReminders().map { reminders -> reminders.mapValues { it.value.lead } },
        selected,
    ) { loaded, filters, place, reminders, selected ->
        val events = loaded.events
        SkyEventsUiState(
            month = loaded.month,
            isCurrentMonth = loaded.month == currentMonth,
            isLoading = events == null,
            weeks = events.orEmpty().filter { filters.isEmpty() || it.category in filters }.byWeek(loaded.month),
            filters = filters,
            isLocationLoaded = place != null,
            location = place?.location,
            reminders = reminders,
            // The month's copy is the fresh one: it follows a change of place.
            selected = selected?.let { open -> events?.firstOrNull { it.id == open.id } ?: open },
            now = timeProvider.now(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        SkyEventsUiState(month = month.value),
    )

    init {
        savedStateHandle.toRoute<SkyEventsRoute>().event?.let(::openEvent)
    }

    fun onPreviousMonth() = month.update { it.minusMonths(1) }

    fun onNextMonth() = month.update { it.plusMonths(1) }

    fun onThisMonth() {
        month.value = currentMonth
    }

    fun onFilterToggle(category: SkyEventCategory) =
        filters.update { if (category in it) it - category else it + category }

    fun onEventClick(event: SkyEvent) {
        selected.value = event
    }

    fun onSheetDismiss() {
        selected.value = null
    }

    /** Turns the event's reminder on with [lead], or off with null. */
    fun onReminderChange(eventId: String, lead: SkyEventReminderLead?) {
        viewModelScope.launch { setReminder(eventId, lead) }
    }

    private fun openEvent(eventId: String) {
        viewModelScope.launch {
            val event = findEvent(eventId) ?: return@launch
            month.value = YearMonth.from(event.time.atZone(zone))
            selected.value = event
        }
    }

    /** Groups by calendar week (the locale's first day), clipped to [shown]. Sorted input stays sorted. */
    private fun List<SkyEvent>.byWeek(shown: YearMonth): List<SkyEventWeek> =
        groupBy { it.time.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) }
            .map { (weekStart, events) ->
                SkyEventWeek(
                    firstDay = maxOf(weekStart, shown.atDay(1)),
                    lastDay = minOf(weekStart.plusDays(DAYS_PER_WEEK - 1), shown.atEndOfMonth()),
                    events = events,
                )
            }

    private data class MonthEvents(val month: YearMonth, val events: List<SkyEvent>?)

    private data class LoadedPlace(val location: ObserverLocation?)

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
        const val DAYS_PER_WEEK = 7L
    }
}
