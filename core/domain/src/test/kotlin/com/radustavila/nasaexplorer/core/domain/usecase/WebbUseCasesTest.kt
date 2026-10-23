package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.model.WebbGap
import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.core.testing.data.TestWebb
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.fake.FakeWebbScheduleRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebbUseCasesTest {

    private val metadata = FakeSyncMetadataRepository()
    private val clock = FakeTimeProvider(currentInstant = TestWebb.NOW)
    private val repository = FakeWebbScheduleRepository()
    private val week = TestWebb.week()

    private val refreshSchedule = RefreshWebbScheduleUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `the schedule is asked for at most once in 20 hours unless forced`() = runTest {
        refreshSchedule()
        clock.currentInstant = clock.currentInstant
            .plus(RefreshWebbScheduleUseCase.MIN_REFRESH_INTERVAL)
            .minusSeconds(1)

        assertTrue(refreshSchedule() is SourceRefreshResult.Skipped)
        refreshSchedule(force = true)

        assertEquals(2, repository.refreshCount)
        assertEquals(clock.currentInstant, metadata.statusOf(SyncSource.JWST_SCHEDULE).lastSuccessAt)
    }

    @Test
    fun `a failed refresh is recorded and stays due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        refreshSchedule()
        refreshSchedule()

        assertEquals(2, repository.refreshCount)
        assertTrue(metadata.statusOf(SyncSource.JWST_SCHEDULE).isFailing)
    }

    @Test
    fun `the visit on at a fixed clock is current, with the next three after it`() {
        val now = checkNotNull(week.statusAt(TestWebb.NOW))

        assertEquals(TestWebb.WASP_39, now.current)
        assertNull(now.previous)
        assertNull(now.gap)
        assertEquals(listOf(TestWebb.JUPITER, TestWebb.DARK, TestWebb.CARINA), now.upNext)
        assertFalse(now.isStale)
        assertEquals(week.fetchedAt, now.fetchedAt)
    }

    @Test
    fun `a calibration visit is current like any other`() {
        val now = checkNotNull(week.statusAt(Instant.parse("2026-10-23T06:40:00Z")))

        assertEquals(TestWebb.FINE_PHASING, now.current)
        assertTrue(now.current!!.isCalibration)
        assertEquals(TestWebb.WASP_39, now.upNext.first())
    }

    @Test
    fun `a short gap between visits is a slew, a long one an idle pause`() {
        // Fine phasing ends 06:58:37, WASP-39 starts 07:05.
        val slewing = checkNotNull(week.statusAt(Instant.parse("2026-10-23T07:00:00Z")))
        // WASP-39 ends 15:38, Jupiter starts 16:55.
        val idle = checkNotNull(week.statusAt(Instant.parse("2026-10-23T16:00:00Z")))

        assertNull(slewing.current)
        assertEquals(WebbGap.SLEWING, slewing.gap)
        assertEquals(TestWebb.FINE_PHASING, slewing.previous)
        assertEquals(TestWebb.WASP_39, slewing.upNext.first())
        assertEquals(WebbGap.IDLE, idle.gap)
        assertEquals(TestWebb.WASP_39, idle.previous)
        assertEquals(listOf(TestWebb.JUPITER, TestWebb.DARK, TestWebb.CARINA), idle.upNext)
    }

    @Test
    fun `before the first visit and after the last the gap says so`() {
        val before = checkNotNull(week.statusAt(Instant.parse("2026-10-22T23:00:00Z")))
        val after = checkNotNull(week.statusAt(Instant.parse("2026-10-24T08:00:00Z")))

        assertEquals(WebbGap.NOT_STARTED, before.gap)
        assertEquals(TestWebb.FINE_PHASING, before.upNext.first())
        assertEquals(WebbGap.SCHEDULE_ENDED, after.gap)
        assertEquals(TestWebb.CARINA, after.previous)
        assertTrue(after.upNext.isEmpty())
    }

    @Test
    fun `a schedule whose week began more than 8 days ago is stale`() {
        val start = TestWebb.FINE_PHASING.start

        assertFalse(week.statusAt(start.plus(Duration.ofDays(8)))!!.isStale)
        assertTrue(week.statusAt(start.plus(Duration.ofDays(8)).plusSeconds(1))!!.isStale)
        assertNull(WebbSchedule(emptyList(), TestWebb.NOW).statusAt(TestWebb.NOW))
    }

    @Test
    fun `calibration, engineering and untargeted visits are calibration and not searched for`() {
        val unload = TestWebb.visit(visitType = "FULL BIASED MOMENTUM UNLOAD", instrument = "", targetName = null)

        assertTrue(TestWebb.FINE_PHASING.isCalibration)
        assertTrue(TestWebb.DARK.isCalibration)
        assertTrue(unload.isCalibration)
        assertNull(TestWebb.FINE_PHASING.searchQuery)
        assertFalse(TestWebb.JUPITER.isCalibration)
        assertEquals("NGC 3324", TestWebb.CARINA.searchQuery)
        assertEquals("2731", TestWebb.CARINA.program)
    }

    @Test
    fun `repeated visits of one target read as one in up next`() {
        val repeat = TestWebb.JUPITER.copy(visitId = "4802:12:2", start = TestWebb.JUPITER.end.plusSeconds(60))
        val sameAsNow = TestWebb.WASP_39.copy(visitId = "1366:4:2", start = TestWebb.WASP_39.end.plusSeconds(30))
        val schedule = week.copy(visits = week.visits + repeat + sameAsNow)

        val now = checkNotNull(schedule.statusAt(TestWebb.NOW))

        assertEquals(listOf(TestWebb.JUPITER, TestWebb.DARK, TestWebb.CARINA), now.upNext)
    }

    @Test
    fun `planning labels are not searched for`() {
        assertNull(TestWebb.visit(targetName = "input_lulls_00").searchQuery)
        assertNull(TestWebb.visit(targetName = "o16").searchQuery)
        assertEquals("V339 Del", TestWebb.visit(targetName = "V339-Del").searchQuery)
    }

    @Test
    fun `now follows the stored schedule and the clock`() = runTest {
        val observeNow = ObserveWebbNowUseCase(repository, clock)

        observeNow().test {
            assertNull(awaitItem())
            repository.schedule.value = week
            assertEquals(TestWebb.WASP_39, awaitItem()?.current)
            clock.currentInstant = Instant.parse("2026-10-23T17:00:00Z")
            assertEquals(TestWebb.JUPITER, awaitItem()?.current)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
