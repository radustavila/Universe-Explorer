package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * How recent an event's latest report is. EONET rarely closes wildfires, so an open wildfire may
 * not have been reported for weeks; its age, not its open status, decides whether it is shown.
 * Other categories are closed by their sources and are always [Current].
 */
enum class EventRecency {
    /** Reported in the last [CURRENT_DAYS] days. */
    Current,

    /** Last reported 8 to [LATEST_DAYS] days ago: shown by default, muted. */
    Latest,

    /** Last reported 15 to [HISTORY_DAYS] days ago: only shown on request, faded. */
    History,

    /** No report for more than [HISTORY_DAYS] days: never shown. */
    Expired,
    ;

    /** Whether the events map and the Today card show it without the history toggle. */
    val isShownByDefault: Boolean get() = this == Current || this == Latest

    companion object {
        const val CURRENT_DAYS = 7
        const val LATEST_DAYS = 14
        const val HISTORY_DAYS = 42

        fun ofAge(days: Long): EventRecency = when {
            days <= CURRENT_DAYS -> Current
            days <= LATEST_DAYS -> Latest
            days <= HISTORY_DAYS -> History
            else -> Expired
        }
    }
}

/** Whole UTC days between the latest report and [today]; never negative. */
fun EarthEvent.reportAgeDays(today: LocalDate): Long =
    ChronoUnit.DAYS.between(lastUpdate.atOffset(ZoneOffset.UTC).toLocalDate(), today).coerceAtLeast(0)

/** The event's [EventRecency] on [today] (a UTC date, as `TimeProvider.today()` returns). */
fun EarthEvent.recency(today: LocalDate): EventRecency =
    if (category == EarthEventCategory.WILDFIRES) EventRecency.ofAge(reportAgeDays(today)) else EventRecency.Current
