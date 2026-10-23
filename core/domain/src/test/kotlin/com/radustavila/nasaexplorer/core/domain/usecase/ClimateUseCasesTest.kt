package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.testing.data.TestClimate
import com.radustavila.nasaexplorer.core.testing.fake.FakeClimateRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateUseCasesTest {

    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-23T09:00:00Z"))
    private val metadata = FakeSyncMetadataRepository()
    private val repository = FakeClimateRepository()
    private val refresh = RefreshClimateUseCase(repository, RefreshSourceUseCase(metadata, clock))

    @Test
    fun `every indicator refreshes once, then not again within the week`() = runTest {
        val first = refresh()
        clock.currentInstant = clock.currentInstant.plus(Duration.ofDays(6))
        val second = refresh()

        assertEquals(ClimateIndicator.entries.toSet(), first.keys)
        assertTrue(first.values.all { it is SourceRefreshResult.Completed })
        assertTrue(second.values.all { it is SourceRefreshResult.Skipped })
        assertEquals(ClimateIndicator.entries.toSet(), repository.refreshed.toSet())
        assertEquals(4, repository.refreshed.size)
    }

    @Test
    fun `after a week, or when forced, the files download again`() = runTest {
        refresh()
        refresh(force = true, indicators = listOf(ClimateIndicator.SEA_LEVEL))
        clock.currentInstant = clock.currentInstant.plus(Duration.ofDays(7))
        refresh()

        assertEquals(9, repository.refreshed.size)
    }

    @Test
    fun `a failure leaves the source due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)
        refresh(indicators = listOf(ClimateIndicator.CO2))
        refresh(indicators = listOf(ClimateIndicator.CO2))

        assertEquals(2, repository.refreshed.size)
    }

    @Test
    fun `one indicator is observed from the stored ones`() = runTest {
        repository.series.value = listOf(TestClimate.co2(), TestClimate.arcticSeaIce())
        val observe = ObserveClimateUseCase(repository)

        assertEquals(TestClimate.arcticSeaIce(), observe(ClimateIndicator.ARCTIC_SEA_ICE).first())
        assertNull(observe(ClimateIndicator.SEA_LEVEL).first())
        assertEquals(2, observe().first().size)
    }
}
