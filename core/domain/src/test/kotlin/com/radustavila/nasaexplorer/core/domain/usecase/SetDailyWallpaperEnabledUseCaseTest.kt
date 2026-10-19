package com.radustavila.nasaexplorer.core.domain.usecase

import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncMetadataRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSyncScheduler
import com.radustavila.nasaexplorer.core.testing.fake.FakeUserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDailyWallpaperEnabledUseCaseTest {

    private val preferences = FakeUserPreferencesRepository()
    private val metadata = FakeSyncMetadataRepository(initialLastWallpaper = LocalDate.of(2026, 9, 10))
    private val syncScheduler = FakeSyncScheduler()
    private val setEnabled = SetDailyWallpaperEnabledUseCase(preferences, metadata, syncScheduler)

    @Test
    fun `turning it on is applied by a sync rather than here`() = runTest {
        setEnabled(true)

        assertTrue(preferences.currentDailyWallpaperEnabled)
        assertEquals(1, syncScheduler.requestSyncCallCount)
    }

    @Test
    fun `turning it off forgets the last applied entry`() = runTest {
        setEnabled(false)

        assertFalse(preferences.currentDailyWallpaperEnabled)
        assertNull(metadata.currentLastWallpaper)
        assertEquals(0, syncScheduler.requestSyncCallCount)
    }
}
