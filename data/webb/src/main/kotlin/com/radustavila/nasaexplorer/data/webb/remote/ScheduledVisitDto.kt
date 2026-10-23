package com.radustavila.nasaexplorer.data.webb.remote

import java.time.Duration
import java.time.Instant

/** A prime visit as [WebbScheduleParser] read it from a weekly file, with its parallels folded in. */
data class ScheduledVisitDto(
    val visitId: String,
    val start: Instant,
    val duration: Duration,
    val visitType: String,
    val instrument: String,
    val targetName: String?,
    val category: String?,
    val keywords: String?,
    val parallels: List<String>,
)
