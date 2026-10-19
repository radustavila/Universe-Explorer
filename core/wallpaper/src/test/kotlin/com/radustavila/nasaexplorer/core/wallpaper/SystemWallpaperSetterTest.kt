package com.radustavila.nasaexplorer.core.wallpaper

import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The setter runs inside the sync worker, so whatever goes wrong must come back as "not set" rather
 * than as an exception that would fail the sync.
 */
@RunWith(RobolectricTestRunner::class)
class SystemWallpaperSetterTest {

    private val setter = SystemWallpaperSetter(
        context = ApplicationProvider.getApplicationContext(),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `an entry without a still image is not set`() = runTest {
        val video = TestApods.video().copy(thumbnailUrl = null)

        assertFalse(setter.setWallpaper(video))
    }

    @Test
    fun `a picture that cannot be loaded is reported as not set`() = runTest {
        val image = TestApods.image().copy(
            url = "file:///nonexistent/picture.jpg",
            hdUrl = "file:///nonexistent/picture_hd.jpg",
        )

        assertFalse(setter.setWallpaper(image))
    }
}
