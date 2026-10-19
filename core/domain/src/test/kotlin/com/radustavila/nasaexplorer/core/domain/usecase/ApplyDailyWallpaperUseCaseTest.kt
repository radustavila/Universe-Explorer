package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeWallpaperSetter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyDailyWallpaperUseCaseTest {

    private val repository = FakeApodRepository()
    private val metadata = FakeSyncMetadataRepository()
    private val wallpaperSetter = FakeWallpaperSetter()

    @Test
    fun `nothing is set until the user opts in`() = runTest {
        repository.setEntries(listOf(TestApods.image()))

        useCase(enabled = false).invoke()

        assertTrue(wallpaperSetter.applied.isEmpty())
    }

    @Test
    fun `the newest picture is set once and remembered`() = runTest {
        val latest = TestApods.image()
        repository.setEntries(listOf(latest, TestApods.image(date = latest.date.minusDays(1))))

        val apply = useCase(enabled = true)
        apply()
        apply()

        assertEquals(listOf(latest), wallpaperSetter.applied)
        assertEquals(latest.date, metadata.currentLastWallpaper)
    }

    @Test
    fun `a video entry leaves the wallpaper alone`() = runTest {
        repository.setEntries(listOf(TestApods.video()))

        useCase(enabled = true).invoke()

        assertTrue(wallpaperSetter.applied.isEmpty())
    }

    @Test
    fun `a wallpaper that could not be set is tried again next time`() = runTest {
        repository.setEntries(listOf(TestApods.image()))
        wallpaperSetter.succeeds = false

        useCase(enabled = true).invoke()

        assertNull(metadata.currentLastWallpaper)
    }

    private fun useCase(enabled: Boolean) = ApplyDailyWallpaperUseCase(
        apodRepository = repository,
        syncMetadataRepository = metadata,
        userPreferencesRepository = FakeUserPreferencesRepository(initialDailyWallpaperEnabled = enabled),
        wallpaperSetter = wallpaperSetter,
    )
}
