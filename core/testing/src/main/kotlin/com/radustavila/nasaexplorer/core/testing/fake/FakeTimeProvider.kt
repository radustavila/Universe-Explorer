package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import java.time.Instant
import java.time.LocalDate

class FakeTimeProvider(
    var currentInstant: Instant = Instant.parse("2026-09-10T12:00:00Z"),
    var currentDate: LocalDate = LocalDate.of(2026, 9, 10),
) : TimeProvider {
    override fun now(): Instant = currentInstant
    override fun today(): LocalDate = currentDate
}
