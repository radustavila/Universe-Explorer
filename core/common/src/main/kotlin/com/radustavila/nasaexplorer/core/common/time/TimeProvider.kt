package com.radustavila.nasaexplorer.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Indirection over the system clock. Date-range pagination and "last synced" bookkeeping are pure
 * date arithmetic, and this keeps them deterministic in tests.
 */
interface TimeProvider {
    fun now(): Instant
    fun today(): LocalDate
}

class SystemTimeProvider(private val zoneId: ZoneId = ZoneId.of("UTC")) : TimeProvider {
    override fun now(): Instant = Instant.now()

    /**
     * NASA publishes the picture of the day against a US date. UTC is close enough for a feed and
     * avoids a device-timezone dependency in the date-window arithmetic.
     */
    override fun today(): LocalDate = LocalDate.now(zoneId)
}
