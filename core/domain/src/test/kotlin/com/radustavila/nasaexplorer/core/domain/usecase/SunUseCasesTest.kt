package com.radustavila.nasaexplorer.core.domain.usecase

import app.cash.turbine.test
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sync.SourceRefreshResult
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.model.SyncSource
import com.radustavila.nasaexplorer.core.testing.fake.FakeSunImageRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunUseCasesTest {

    private val now = Instant.parse("2026-10-22T16:00:00Z")
    private val clock = FakeTimeProvider(currentInstant = now)
    private val metadata = FakeSyncMetadataRepository()
    private val repository = FakeSunImageRepository()
    private val refresh = RefreshSunImageUseCase(repository, RefreshSourceUseCase(metadata, clock), clock)

    @Test
    fun `the Sun follows the chosen channel with its picture and video`() = runTest {
        val corona = FakeSunImageRepository.image(SunChannel.CORONA, now.minusSeconds(600))
        repository.images.value = mapOf(SunChannel.CORONA to corona)

        ObserveSunUseCase(repository)().test {
            val visible = awaitItem()
            assertEquals(SunChannel.VISIBLE_LIGHT, visible.channel)
            assertNull(visible.image)

            SelectSunChannelUseCase(repository)(SunChannel.CORONA)
            val chosen = awaitItem()
            assertEquals(corona, chosen.image)
            assertEquals(repository.videoUrl(SunChannel.CORONA), chosen.videoUrl)
        }
    }

    @Test
    fun `a picture younger than SDO's cadence is not asked for again`() = runTest {
        val updatedAt = now.minusSeconds(5 * 60)
        repository.images.value = mapOf(SunChannel.CORONA to FakeSunImageRepository.image(SunChannel.CORONA, updatedAt))

        val outcome = refresh(SunChannel.CORONA)

        assertEquals(SourceRefreshResult.Skipped(updatedAt.plus(RefreshSunImageUseCase.SDO_CADENCE)), outcome)
        assertTrue(repository.refreshes.isEmpty())
    }

    @Test
    fun `an old or missing picture is downloaded and the outcome recorded as SDO`() = runTest {
        repository.images.value = mapOf(
            SunChannel.CORONA to FakeSunImageRepository.image(SunChannel.CORONA, now.minusSeconds(20 * 60)),
        )

        refresh(SunChannel.CORONA)
        refresh(SunChannel.FLARES)

        assertEquals(listOf(SunChannel.CORONA, SunChannel.FLARES), repository.refreshes)
        assertEquals(now, metadata.statusOf(SyncSource.SDO).lastSuccessAt)
    }

    @Test
    fun `pull to refresh asks even for a fresh picture`() = runTest {
        repository.images.value = mapOf(
            SunChannel.CORONA to FakeSunImageRepository.image(SunChannel.CORONA, now.minusSeconds(60)),
        )

        refresh(SunChannel.CORONA, force = true)

        assertEquals(listOf(SunChannel.CORONA), repository.refreshes)
    }

    @Test
    fun `a failure is recorded and leaves the source due`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.NoConnection)

        refresh(SunChannel.VISIBLE_LIGHT)
        refresh(SunChannel.VISIBLE_LIGHT)

        assertEquals(2, repository.refreshes.size)
        assertTrue(metadata.statusOf(SyncSource.SDO).isFailing)
    }
}
